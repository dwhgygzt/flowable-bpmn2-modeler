package com.middol.flowable.modeler;

import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.*;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ApplicationTest {


    private static final Logger logger = LoggerFactory.getLogger(ApplicationTest.class);

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private FormService formService;
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    private TaskService taskService;


    @Autowired
    HistoryService historyService;

    @Test
    public void getProcessList() {
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
        List<ProcessDefinition> list = query.active().latestVersion().orderByProcessDefinitionName().asc().list();

        if (list != null) {
            list.forEach(item -> {
                logger.info(item.getDeploymentId() + "," + item.getId() + "," + item.getKey() + "," + item.getName());
            });
        }
    }

    /**
     * 是否多实例节点
     */
    @Test
    public void hasMultiInstanceLoop() {
        String processKey = "TUONEI_PROCESS_GYSSP2";
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        FlowElement f = process.getFlowElement("D1-2-1-D");
        if (f instanceof UserTask) {
            logger.info("是否多实例={}, 是否串行={}",
                    ((UserTask) f).hasMultiInstanceLoopCharacteristics(),
                    ((UserTask) f).getLoopCharacteristics().isSequential()
            );
        }

    }

    /**
     * 判断是否为并行网关上的节点
     */
    @Test
    public void isParallelGatewayUserTask() {
        // 检查上游是否有并行网关节点
        int incomingCheckResult = 0;
        // 流程节点中的id
        String flowNodeId = "T3";
        String processKey = "P-TEST";
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Map<String, FlowElement> flowElementMap = process.getFlowElementMap();
        // 获取要判断的目标节点 和 其上下游节点集合
        FlowElement targetFlowNode = flowElementMap.get(flowNodeId);
        if (!(targetFlowNode instanceof FlowNode)) {
            logger.warn("{} 非流程节点，无法判断是否为并行网关上的节点", flowNodeId);
            return;
        }

        List<SequenceFlow> incomingFlows = ((FlowNode) targetFlowNode).getIncomingFlows();
        // 开始递归查找目标节点的上游网关节点，一直找到开始节点为止
        while (!CollectionUtils.isEmpty(incomingFlows)) {
            SequenceFlow inCommintSequenceFlow = incomingFlows.get(0);
            FlowElement inCommintFlowNode = flowElementMap.get(inCommintSequenceFlow.getSourceRef());
            if (inCommintFlowNode instanceof FlowNode) {
                incomingFlows = ((FlowNode) inCommintFlowNode).getIncomingFlows();
            } else {
                continue;
            }
            // 当遇到网关时判断
            if (inCommintFlowNode instanceof ParallelGateway || inCommintFlowNode instanceof InclusiveGateway) {
                logger.info("找到上游网关节点 {} {}", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                FlowNode inCommintFlowNodeIns = (FlowNode) inCommintFlowNode;
                int outgoingFlowsSize = inCommintFlowNodeIns.getOutgoingFlows().size();
                int incomingFlowsSize = inCommintFlowNodeIns.getIncomingFlows().size();
                if (outgoingFlowsSize > 1) {
                    incomingCheckResult = incomingCheckResult + 1;
                    logger.info("找到上游网关节点 {} {}，且作为判断依据，出多条线，进一条线 +1操作", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                } else if (incomingFlowsSize > 1 && outgoingFlowsSize == 1) {
                    incomingCheckResult = incomingCheckResult - 1;
                    logger.info("找到上游网关节点 {} {}，且作为判断依据，进多条线，出一条线 -1操作", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                } else {
                    logger.info("找到上游网关节点 {} {}，但不符合条件，进和出都为一条线，不作为判断依据", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                }
            }
        }

        if (incomingCheckResult > 0) {
            logger.info("{} 是并行网关上的节点 ooooooo", flowNodeId);
        } else {
            logger.info("{} 不是并行网关上的节点 xxxxxx", flowNodeId);
        }

    }


    @Test
    public void getAllFlowElements() {
        String processKey = "TUONEI_PROCESS_GYSSP2";
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Collection<FlowElement> collection = process.getFlowElements();
        List<FlowElement> flowElements = new ArrayList<>(collection);
        for (int i = 0; i < flowElements.size(); i++) {
            FlowElement task = flowElements.get(i);

            logger.info("用户任务" + (i + 1));
            logger.info("id=" + task.getId() + ", name=" + task.getName() + ", type=" + task.getClass().getSimpleName());
        }
    }

    @Test
    public void getAllUserTask() {
        String processKey = "TUONEI_PROCESS_GYSSP2";
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        List<UserTask> flowElements = process.findFlowElementsOfType(UserTask.class);
        if (flowElements != null) {
            for (int i = 0; i < flowElements.size(); i++) {
                UserTask task = flowElements.get(i);

                logger.info("用户任务" + (i + 1));
                logger.info("id=" + task.getId() + ", name=" + task.getName() + ", Assignee=" + task.getAssignee() + ", Category=" + task.getCategory());
            }
        }
    }

    @Test
    public void startProcess() {
        String processKey = "P-TEST";
        Map<String, Object> variables = new HashMap<>();
        variables.put("skipTest", 1);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                processKey, "BUSINESS-KEY005", variables);
        // 50ff0dc9-273e-11eb-b659-f83441e50f2a
        logger.info("流程启动成功 processInstanceId = {}", processInstance.getId());

    }

    @Test
    public void listTodoTask() {
        String instanceId = "575e9c44-2317-11eb-a3f3-f83441e50f2a";
        String instanceId2 = "2f1a30c2-215e-11eb-b4f0-f83441e50f2a";
        List<Task> taskList = taskService.createTaskQuery().processInstanceId(instanceId).active().orderByTaskCreateTime().desc().list();
        if (taskList != null) {
            taskList.forEach(item -> logger.info(item.getId() + "," + item.getName() + "," + item.getAssignee() + "," + item.getTaskDefinitionKey()));
        }
    }

    @Test
    public void claim() {
        String taskId = "2c6ef8be-20ed-11eb-be2f-f83441e50f2a";
        taskService.claim(taskId, "zhangsan2");
    }

    @Test
    public void commit() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("auditResult", 1);
        variables.put("commitUserD4", "wangwu2");
        variables.put("commitUserD5", "zhaoliu2");
        variables.put("auditUserD2List", Arrays.asList("zhangsan3", "lisi3"));

        String taskId = "97d5d12e-273e-11eb-a336-f83441e50f2a";
        taskService.complete(taskId);

        //taskId = "5c28dc46-231c-11eb-982c-f83441e50f2a";
        //taskService.complete(taskId, variables);

    }

    @Test
    public void suspendProcessDef() {
        String processKey = "TUONEI_PROCESS_GYSSP";
        repositoryService.suspendProcessDefinitionByKey(processKey);

    }

    @Test
    public void suspendProcessInstance() {
        String instanceId = "2c6b9d4e-20ed-11eb-be2f-f83441e50f2a";
        runtimeService.suspendProcessInstanceById(instanceId);
    }

    @Test
    public void activateProcessInstance() {
        String instanceId = "2c6b9d4e-20ed-11eb-be2f-f83441e50f2a";
        runtimeService.activateProcessInstanceById(instanceId);
    }

    @Test
    public void addMultiInstanceExecution() {
        String instanceId = "dbfd5e76-21be-11eb-9c0a-f83441e50f2a";
        String taskKey = "D1-2-1-D";
        Map<String, Object> variables = new HashMap<>();
        variables.put("auditUserD1", "zhaoliu");
        runtimeService.addMultiInstanceExecution(taskKey, instanceId, variables);
    }

    @Test
    public void deleteMultiInstanceExecution() {
        String executionId = "dc001da3-21be-11eb-9c0a-f83441e50f2a";
        runtimeService.deleteMultiInstanceExecution(executionId, false);
    }

    @Test
    public void deleteIntance() {
        String instanceId = "bb58bb63-2337-11eb-9978-f83441e50f2a";
        runtimeService.deleteProcessInstance(instanceId, null);
        historyService.deleteHistoricProcessInstance(instanceId);
    }

    @Test
    public void jump() {
        String taskId = "bb5dc485-2337-11eb-9978-f83441e50f2a";
        String newActivityId = "sid-E895030E-021B-4837-AA80-4231A05D89F8";

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        Map<String, Object> param = task.getProcessVariables();
        if (!CollectionUtils.isEmpty(param)) {
            param.put("commitUserD4", "444");
            param.put("commitUserD5", "555");
        } else {
            param = new HashMap<>(2);
            param.put("commitUserD4", "444");
            param.put("commitUserD5", "555");
        }
        taskService.setVariables(taskId, param);

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).list();
        List<String> currentActivityIds = tasks.stream().map(Task::getTaskDefinitionKey).collect(Collectors.toList());

        runtimeService.createChangeActivityStateBuilder().processInstanceId(
                task.getProcessInstanceId())
                .moveActivityIdsToSingleActivityId(currentActivityIds, newActivityId)
                .changeState();
    }

}