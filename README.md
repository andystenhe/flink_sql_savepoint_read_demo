# flink_sql_savepoint_read_demo
## 构建flink-state-processing-api的jar
```
cd .\flink\flink-libraries\flink-state-processing-api
mvn clean install
```

## 修改SavepointJob的配置
SavepointJob是一个读取flink sql的ChangelogNormalize算子内数据的例子
需要根据业务修改代码, 例如:savepointDirectory,saveFilePath,key和value数据结构等

## 运行SavepointJob的main方法