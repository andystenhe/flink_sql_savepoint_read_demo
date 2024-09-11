package org.andy;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.json.JSONObject;
import com.esotericsoftware.kryo.Serializer;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.state.api.ExistingSavepoint;
import org.apache.flink.state.api.Savepoint;
import org.apache.flink.state.api.functions.KeyedStateReaderFunction;
import org.apache.flink.state.api.runtime.OperatorIDGenerator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.runtime.typeutils.RowDataSerializer;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *  savepoint 使用代码
 * @author hexiufeng
 * @date 2024/09/11
 */
public class SavepointJob {

    public static String savepointDirectory = "file:///D:\\tmp\\flink\\savepoint-f680f0-292195460afd";
    public static String saveFilePath = "file:///D:\\tmp\\sp.txt";

    /**
     *  读取flink sql的ChangelogNormalize算子内的数据
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        ExecutionEnvironment.setDefaultLocalParallelism(1);
        ExecutionEnvironment bEnv =
                ExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());
        ExecutionConfig config = bEnv.getConfig();

        // key 的结构, 一般是flink sql建表中的PRIMARY KEY. Debug这个异常StateMigrationException可以看savepoint内的数据结构
        RowType rowType = RowType.of(
                new VarCharType(false, VarCharType.MAX_LENGTH),
                new VarCharType(false, VarCharType.MAX_LENGTH),
                new VarCharType(false, VarCharType.MAX_LENGTH)
        );
        InternalTypeInfo<RowData> keyTypeInfo = InternalTypeInfo.of(rowType);

        // value 的结构, 一般是flink sql建表中的字段
        RowType valueRowType = RowType.of(
                new VarCharType(false, VarCharType.MAX_LENGTH),
                new VarCharType(false, VarCharType.MAX_LENGTH),
                new VarCharType(false, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new IntType(true),
                new IntType(true),
                new VarCharType(true, VarCharType.MAX_LENGTH),
                new VarCharType(true, VarCharType.MAX_LENGTH)
        );

        // 列名
        String[] names = new String[]{
                "device_id", "endpoint_id", "trait_id", "device_type", "model", "real1", "real2", "lvalue", "online_state", "statistic_flag", "user_id", "src_tag"
        };
        InternalTypeInfo<RowData> valueTypeInfo = InternalTypeInfo.of(valueRowType);

        // savepoint的路径, 可以使用flink savepoint命令获得, k8s模式启动也可以
        ExistingSavepoint savepoint = Savepoint.load(bEnv, savepointDirectory, new EmbeddedRocksDBStateBackend());
        //  90bea66de1c231edf33913ecd54406c1是ChangelogNormalize算子的OperatorID的哈希值, readKeyedStateByHexString是我修改flink state processing api的得到
        //  id可以使用org.apache.flink.runtime.checkpoint.Checkpoints.loadCheckpointMetadata查看
        DataSource<String> stringDataSource = savepoint.readKeyedStateByHexString("90bea66de1c231edf33913ecd54406c1", new KeyedStateReaderFunction<RowData, String>() {

            private ValueState<RowData> state;

            @Override
            public void open(Configuration configuration) throws Exception {
                // 根据算子写入的格式获取, 其定义在类MiniBatchDeduplicateFunctionBase
                ValueStateDescriptor<RowData> stateDesc =
                        new ValueStateDescriptor<>("deduplicate-state", valueTypeInfo);
                // 必须执行, 否则readKey方法就没有数据
                state = this.getRuntimeContext().getState(stateDesc);
            }

            @Override
            public void readKey(RowData key, Context context, Collector<String> collector) throws Exception {
                RowData value = state.value();
                JSONObject jsonObject = new JSONObject();
                for (int i = 0; i < value.getArity(); i++) {
                    String fieldName = names[i];
                    jsonObject.set(fieldName, getRowField(i, value, valueTypeInfo.toRowType().getTypeAt(i)));
                }
                collector.collect(jsonObject.toString());
            }
        }, keyTypeInfo, Types.STRING);
        // 保存到本地文件, 后面可以导入数据库, 方便查看.
        stringDataSource.writeAsText(saveFilePath);
        bEnv.execute();
    }

    public static Object getRowField(int index, RowData row, LogicalType type) {
        Object result = null;
        switch (type.asSerializableString()) {
            case "INT":
                result = row.getInt(index);
                break;
            default:
                result = row.getString(index).toString();
        }
        return  result;
    }

}
