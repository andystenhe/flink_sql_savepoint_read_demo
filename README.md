# flink_sql_savepoint_read_demo
这是一个domo说明如何读取flink savepoint中backend的数据.
在官网的例子中是使用uid读取backend数据的, 没有给出读取flink sql的backend数据.


## 构建flink-state-processing-api的jar
原接口是不支持算子的hex string读取数据的, 所以我修改其源码, 
让其支持基于算子hex string提取.
```
cd .\flink\flink-libraries\flink-state-processing-api
mvn clean install
```

## 修改SavepointJob的配置
SavepointJob是一个读取flink sql的ChangelogNormalize算子内数据的例子.
需要根据业务修改代码, 例如:savepointDirectory,saveFilePath,key和value数据结构等

算子的hex string在flink job启动的debug日志中或者demo中的FlinkSnapshotAnalyzer类获取

## 运行SavepointJob的main方法