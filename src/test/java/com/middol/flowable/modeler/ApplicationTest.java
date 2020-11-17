package com.middol.flowable.modeler;

import com.middol.flowable.modeler.dto.ParallelGatwayDTO;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
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
     * 获取所有并行网关内的节点 和 并行网关之间的关系
     */
    @Test
    public void getAllParallelGatewayUserTask() {
        String processKey = "P-TEST4";

        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Map<String, FlowElement> flowElementMap = process.getFlowElementMap();

        List<ParallelGateway> parallelGateways = process.findFlowElementsOfType(ParallelGateway.class);
        List<InclusiveGateway> inclusiveGateways = process.findFlowElementsOfType(InclusiveGateway.class);

        List<Gateway> allParallelGateways = new ArrayList<>(4);
        allParallelGateways.addAll(parallelGateways);
        allParallelGateways.addAll(inclusiveGateways);


        Map<String, ParallelGatwayDTO> forkGatewayMap = new HashMap<>(4);
        Map<String, ParallelGatwayDTO> joinGatewayMap = new HashMap<>(4);

        for (Gateway gateway : allParallelGateways) {
            int outgoingFlowsSize = gateway.getOutgoingFlows().size();
            // 从 fork网关节点开始查找
            if (outgoingFlowsSize > 1 && !forkGatewayMap.containsKey(gateway.getId())) {
                ParallelGatwayDTO dto = new ParallelGatwayDTO();
                dto.setForkId(gateway.getId());
                forkGatewayMap.put(gateway.getId(), dto);

                loopForkParallelGateway(dto, gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }
        }

    }

    private void loopForkParallelGateway(ParallelGatwayDTO dto,
                                         List<SequenceFlow> outgoingFlows,
                                         Map<String, ParallelGatwayDTO> forkGatewayMap,
                                         Map<String, FlowElement> flowElementMap) {
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }
        for (SequenceFlow item : outgoingFlows) {
            FlowElement flowElement = flowElementMap.get(item.getTargetRef());
            if (flowElement instanceof UserTask) {
                dto.getUserTasks().add((UserTask) flowElement);
                // 递归取节点
                loopForkParallelGateway(dto, ((FlowNode) flowElement).getOutgoingFlows(), forkGatewayMap, flowElementMap);
            } else if (flowElement instanceof ParallelGateway || flowElement instanceof InclusiveGateway) {
                Gateway gateway = (Gateway) flowElement;
                // 新的网关节点
                ParallelGatwayDTO childDto = dto;
                // 从 fork网关节点开始查找
                if (gateway.getOutgoingFlows().size() > 1) {
                    childDto = new ParallelGatwayDTO();
                    childDto.setForkId(flowElement.getId());
                    forkGatewayMap.put(flowElement.getId(), childDto);

                    dto.getChildParallelGatways().add(childDto);
                }else if(gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1){
                    // join 类型的并行网关

                }

                // 递归取节点
                loopForkParallelGateway(childDto, gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }
        }
    }

    /**
     * 判断是否为并行网关上的节点
     */
    @Test
    public void isParallelGatewayUserTask() {
        // 流程节点中的id
        String flowNodeId = "T5";
        String processKey = "P-TEST2";

        boolean checkResult = false;
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Map<String, FlowElement> flowElementMap = process.getFlowElementMap();
        // 获取要判断的目标节点 和 其上游节点集合
        FlowElement targetFlowElement = flowElementMap.get(flowNodeId);
        if (!(targetFlowElement instanceof FlowNode)) {
            logger.warn("{} 非流程节点，无法判断是否为并行网关上的节点", flowNodeId);
            return;
        }
        // 目标节点的上游节点集合
        List<SequenceFlow> incomingFlowsCount = ((FlowNode) targetFlowElement).getIncomingFlows();
        // 目标节点的所有上游路线全部要判断
        for (SequenceFlow sequenceFlow : incomingFlowsCount) {
            int forIndex = 0;
            // 检查上游是否有并行网关节点
            int incomingCheckResult = 0;
            List<SequenceFlow> incomingFlows = ((FlowNode) targetFlowElement).getIncomingFlows();
            // 开始递归查找目标节点的上游网关节点，一直找到开始节点为止
            while (!CollectionUtils.isEmpty(incomingFlows)) {
                SequenceFlow inCommintSequenceFlow;
                if (forIndex == 0) {
                    inCommintSequenceFlow = sequenceFlow;
                } else {
                    inCommintSequenceFlow = incomingFlows.get(0);
                }
                FlowElement inCommintFlowNode = flowElementMap.get(inCommintSequenceFlow.getSourceRef());
                if (inCommintFlowNode instanceof FlowNode) {
                    incomingFlows = ((FlowNode) inCommintFlowNode).getIncomingFlows();
                } else {
                    continue;
                }
                // 当遇到网关时判断
                if (inCommintFlowNode instanceof ParallelGateway || inCommintFlowNode instanceof InclusiveGateway) {
                    FlowNode inCommintFlowNodeIns = (FlowNode) inCommintFlowNode;
                    int outgoingFlowsSize = inCommintFlowNodeIns.getOutgoingFlows().size();
                    int incomingFlowsSize = inCommintFlowNodeIns.getIncomingFlows().size();
                    if (outgoingFlowsSize > 1) {
                        incomingCheckResult++;
                        logger.debug("----找到上游网关fork节点 id={} name={}，且作为判断依据， +1操作", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                    } else if (incomingFlowsSize > 1 && outgoingFlowsSize == 1) {
                        incomingCheckResult--;
                        logger.debug("----找到上游网关join节点 id={} name={}，且作为判断依据， -1操作", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                    } else {
                        logger.debug("----找到上游网关节点 id={} name={}，但不符合条件，进和出都为一条线，不作为判断依据", inCommintFlowNode.getId(), inCommintFlowNode.getName());
                    }
                }

                forIndex++;
            }

            if (incomingCheckResult > 0) {
                checkResult = true;
                logger.info(">>>>>在路线{}上，{} 是并行网关上的节点 ooooooo", sequenceFlow.getSourceRef(), flowNodeId);
            } else {
                logger.info(">>>>>在路线{}上，{} 不是并行网关上的节点 xxxxxx", sequenceFlow.getSourceRef(), flowNodeId);
            }
        }

        logger.info(">>>>>>>>>最终结果【{}】并行网关上的节点", checkResult ? "是" : "否");

    }


    // 获取本节点相关流程线上执行过的流程节点
    @Test
    public void getMyFlowHisTask() {
        // 流程节点中的id
        String flowNodeId = "T5";
        String processKey = "P-TEST3";
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Map<String, FlowElement> flowElementMap = process.getFlowElementMap();
        // 获取要判断的目标节点 和 其上游节点集合
        FlowElement targetFlowElement = flowElementMap.get(flowNodeId);
        if (!(targetFlowElement instanceof FlowNode)) {
            logger.warn("{} 非流程节点，无法获取相关流程线上执行过的流程节点", flowNodeId);
            return;
        }

        // 目标节点的上游节点集合
        List<UserTask> tasks = new ArrayList<>(8);
        Map<String, UserTask> taskMap = new HashMap<>(8);
        loopIncomingFlows(((FlowNode) targetFlowElement).getIncomingFlows(), tasks, taskMap, flowElementMap);

        tasks.forEach(item -> logger.info("" + item.getId() + "," + item.getName() + "," + item.getAssignee()));

    }

    /**
     * 递归获取某个节点前面的所有相关节点
     *
     * @param incomingFlows  相邻来源节点引用集合
     * @param tasks          最后输出的 List结果集
     * @param taskMap        最后输出的 Map结果集
     * @param flowElementMap Process # getFlowElementMap()
     */
    private void loopIncomingFlows(
            List<SequenceFlow> incomingFlows,
            List<UserTask> tasks,
            Map<String, UserTask> taskMap,
            Map<String, FlowElement> flowElementMap) {
        if (!CollectionUtils.isEmpty(incomingFlows)) {
            for (SequenceFlow item : incomingFlows) {
                FlowElement flowElement = flowElementMap.get(item.getSourceRef());
                if (flowElement instanceof FlowNode) {
                    if (flowElement instanceof UserTask) {
                        UserTask task = (UserTask) flowElement;
                        if (!taskMap.containsKey(task.getId())) {
                            tasks.add(task);
                            taskMap.put(task.getId(), task);
                        }
                    }
                    loopIncomingFlows(((FlowNode) flowElement).getIncomingFlows(), tasks, taskMap, flowElementMap);
                }
            }
        }
    }

    @Test
    public void getAllFlowElements() {
        String processKey = "P-TEST";
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
                processKey, "BUSINESS-KEY006", variables);
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

        String taskId = "e8d03271-27e3-11eb-bc4f-f83441e50f2a";
        taskService.complete(taskId);

        //taskId = "3e1429da-27d8-11eb-8b88-f83441e50f2a";
        //taskService.complete(taskId);

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
        String taskId = "be2ade72-27e0-11eb-88b8-f83441e50f2a";
        String newActivityId = "T2-2-0";

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