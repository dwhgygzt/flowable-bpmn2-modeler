# Flowable6.4.2 bpmn2 模型画图应用

## 一、代码说明
1. 自动创建 flowable 相关的表

2. 记得修改 application-devp.yml 中的数据库连接URL

3. 直接访问 http://localhost:8082/ 即可打开模型设计首页

4. 不需要登录，直接默认当前用户为admin管理员

5. 日志级别记得修改 application-devp.yml 中的 logging.level.root 值


## 二、关于多租户

架构上认为多租户不一定要要求flowable支持多租户， 个人认为flowable只是一个流程运行引擎，

当流程运行完结时可以完全删除flowable中的数据（不可依赖flowable存储业务历史数据），

多租户架构可以通过不一样的key进行隔离，在查询首页接口和保存模型接口中加入当前用户对应的归属租户号即可。