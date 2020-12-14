package com.middol.flowable.modeler;

import com.alibaba.fastjson.JSONObject;
import com.middol.flowable.modeler.cmd.SaveExecutionCmd;
import com.middol.flowable.modeler.dto.ParallelGatwayDTO;
import com.middol.flowable.modeler.dto.UserTaskNodeDTO;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.impl.cfg.IdGenerator;
import org.flowable.engine.*;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.Execution;
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
    private ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    private TaskService taskService;

    @Autowired
    HistoryService historyService;

    @Autowired
    ManagementService managementService;


    @Test
    public void getProcessList() {
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
        List<ProcessDefinition> list = query.active().latestVersion().orderByProcessDefinitionName().asc().list();

        if (list != null) {
            list.forEach(item -> logger.info(item.getDeploymentId() + "," + item.getId() + "," + item.getKey() + "," + item.getName()));
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
    public Map<String, ParallelGatwayDTO> getAllParallelGatewayUserTask(String processDefinitionId) {
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
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

        for (Gateway gateway : allParallelGateways) {
            int outgoingFlowsSize = gateway.getOutgoingFlows().size();
            // 从 fork网关节点开始查找
            if (outgoingFlowsSize > 1 && !forkGatewayMap.containsKey(gateway.getId())) {
                ParallelGatwayDTO dto = new ParallelGatwayDTO();
                dto.setForkSize(outgoingFlowsSize);
                dto.setForkId(gateway.getId());
                forkGatewayMap.put(gateway.getId(), dto);

                loopForkParallelGateway(dto,
                        gateway.getOutgoingFlows(),
                        forkGatewayMap,
                        flowElementMap);
            }
        }

//        forkGatewayMap.forEach((k, v) -> {
//            logger.info("并行网关k={}, 分支数量={}, 子并行网关数量={}", k, v.getForkSize(), v.getChildParallelGatways().size());
//        });

        // logger.info(JSONObject.toJSONString(forkGatewayMap.get("P1")));

        return forkGatewayMap;
    }

    private void loopForkParallelGateway(ParallelGatwayDTO dto,
                                         List<SequenceFlow> outgoingFlows,
                                         Map<String, ParallelGatwayDTO> forkGatewayMap,
                                         Map<String, FlowElement> flowElementMap) {
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }
        for (SequenceFlow item : outgoingFlows) {
            FlowElement refFlowElement = flowElementMap.get(item.getSourceRef());
            FlowElement targetFlowElement = flowElementMap.get(item.getTargetRef());
            // 设置当前查询的哪一个分支线
            if (refFlowElement instanceof ParallelGateway || refFlowElement instanceof InclusiveGateway) {
                dto.setTmpForkRef(item.getTargetRef());
            }

            if (targetFlowElement instanceof UserTask) {
                UserTask task = (UserTask) targetFlowElement;
                if (!dto.getUserTasks().containsKey(task.getId())) {
                    UserTaskNodeDTO userTaskNodeDTO = new UserTaskNodeDTO();
                    userTaskNodeDTO.setId(task.getId());
                    userTaskNodeDTO.setName(task.getName());
                    userTaskNodeDTO.setAssignee(task.getAssignee());
                    userTaskNodeDTO.setCategory(task.getCategory());
                    userTaskNodeDTO.setFormKey(task.getFormKey());
                    userTaskNodeDTO.setInParallelGatway(true);
                    userTaskNodeDTO.setParallelGatewayForkRef(dto.getTmpForkRef());
                    userTaskNodeDTO.setForkParallelGatwayId(dto.getForkId());
                    userTaskNodeDTO.setHasMultiInstance(task.hasMultiInstanceLoopCharacteristics());
                    if (task.hasMultiInstanceLoopCharacteristics()) {
                        userTaskNodeDTO.setSequential(task.getLoopCharacteristics().isSequential());
                    }
                    dto.getUserTasks().put(task.getId(), userTaskNodeDTO);
                }
                // 递归取下面的节点
                loopForkParallelGateway(dto, ((FlowNode) targetFlowElement).getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }

            if (targetFlowElement instanceof ParallelGateway || targetFlowElement instanceof InclusiveGateway) {
                Gateway gateway = (Gateway) targetFlowElement;
                // 遇到新的并行网关节点
                // 从 fork网关节点开始查找
                if (gateway.getOutgoingFlows().size() > 1) {
                    ParallelGatwayDTO childDto = forkGatewayMap.get(gateway.getId());
                    if (childDto == null) {
                        childDto = new ParallelGatwayDTO();
                    }
                    childDto.setForkSize(gateway.getOutgoingFlows().size());
                    childDto.setForkId(targetFlowElement.getId());

                    dto.getChildParallelGatways().add(childDto);
                    childDto.setParentParallelGatwayDTO(dto);

                    forkGatewayMap.put(targetFlowElement.getId(), childDto);
                    // 递归取下面的节点
                    loopForkParallelGateway(childDto, gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
                } else if (gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1) {
                    // 遇到新的join类型的并行网关节点，此时dto为前面与之对应的fork并行网关节点
                    dto.setJoinId(gateway.getId());
                    dto.getUserTasks().forEach((k, v) -> v.setJoinParallelGatwayId(gateway.getId()));

                    //joinGatewayMap.put(gateway.getId(), dto);

                    if (dto.getParentParallelGatwayDTO() == null) {
                        // 本并行网关里面的用户任务递归完毕
                        break;
                    }

                    // 继续父并行网关的递归取
                    loopForkParallelGateway(dto.getParentParallelGatwayDTO(), gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
                }
            }
        }
    }

    public void insertExecutionTest(String gatewayId, String processInstanceId, String processDefinitionId, String tenantId) {
        ExecutionEntityManager executionEntityManager = ((ProcessEngineConfigurationImpl) processEngineConfiguration).getExecutionEntityManager();
        ExecutionEntity executionEntity = executionEntityManager.create();
        IdGenerator idGenerator = processEngineConfiguration.getIdGenerator();
        executionEntity.setId(idGenerator.getNextId());
        executionEntity.setRevision(0);
        executionEntity.setProcessInstanceId(processInstanceId);

        executionEntity.setParentId(processInstanceId);
        executionEntity.setProcessDefinitionId(processDefinitionId);

        executionEntity.setRootProcessInstanceId(processInstanceId);
        ((ExecutionEntityImpl) executionEntity).setActivityId(gatewayId);
        executionEntity.setActive(false);

        executionEntity.setSuspensionState(1);
        executionEntity.setTenantId(tenantId);

        executionEntity.setStartTime(new Date());
        ((ExecutionEntityImpl) executionEntity).setCountEnabled(true);

        managementService.executeCommand(new SaveExecutionCmd(executionEntity));
        //executionEntityManager.insert(executionEntity);
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

    // 获取本节点相同的流程线上的用户任务节点
    @Test
    public void getMyCommonFlowTask() {
        // 流程节点中的id
        String flowNodeId = "T6";
        String processKey = "P-TEST2";
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
        loopIncomingFlows(((FlowNode) targetFlowElement).getIncomingFlows(), tasks, taskMap, flowElementMap, true);

        tasks.forEach(item -> logger.info("" + item.getId() + "," + item.getName() + "," + item.getAssignee()));
    }


    // 获取本节点之前的全部用户任务节点
    @Test
    public void getMyFlowTask() {
        // 流程节点中的id
        String flowNodeId = "T4";
        String processKey = "P-TEST4";
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
        loopIncomingFlows(((FlowNode) targetFlowElement).getIncomingFlows(), tasks, taskMap, flowElementMap, false);

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
            Map<String, FlowElement> flowElementMap,
            boolean isParallelGatewayBreak) {
        if (!CollectionUtils.isEmpty(incomingFlows)) {
            for (SequenceFlow item : incomingFlows) {
                FlowElement flowElement = flowElementMap.get(item.getSourceRef());
                if (isParallelGatewayBreak) {
                    if (flowElement instanceof ParallelGateway || flowElement instanceof InclusiveGateway) {
                        break;
                    }
                }

                if (flowElement instanceof FlowNode) {
                    if (flowElement instanceof UserTask) {
                        UserTask task = (UserTask) flowElement;
                        if (!taskMap.containsKey(task.getId())) {
                            tasks.add(task);
                            taskMap.put(task.getId(), task);
                        }
                    }
                    loopIncomingFlows(((FlowNode) flowElement).getIncomingFlows(), tasks, taskMap, flowElementMap, isParallelGatewayBreak);
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
    public void jumpParallelGataway() {
        String taskId = "67d4aa59-2a51-11eb-8fa3-f83441e50f2a";
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        List<String> newActivityIds = Arrays.asList("T3-2", "T3-1");

        Map<String, ParallelGatwayDTO> forkGatewayMap = getAllParallelGatewayUserTask(task.getProcessDefinitionId());
        forkGatewayMap.forEach((k, v) -> logger.info("并行网关k={}, 分支数量={}, 子并行网关数量={}", k, v.getForkSize(), v.getChildParallelGatways().size()));

        Map<String, UserTaskNodeDTO> parallelGatawayTaskMap = new LinkedHashMap<>(8);

        forkGatewayMap.forEach((k, v) -> parallelGatawayTaskMap.putAll(v.getUserTasks()));

        //判断节点
        String forkParallelGatwayId = "";
        String joinParallelGatwayId = "";
        String parallelGatewayForkRef = "";
        for (String item : newActivityIds) {
            if (!parallelGatawayTaskMap.containsKey(item)) {
                throw new RuntimeException("目标节点非并行网关中的节点");
            }
            UserTaskNodeDTO taskNodeDTO = parallelGatawayTaskMap.get(item);
            if ("".equals(forkParallelGatwayId) || "".equals(joinParallelGatwayId)) {
                forkParallelGatwayId = taskNodeDTO.getForkParallelGatwayId();
                joinParallelGatwayId = taskNodeDTO.getJoinParallelGatwayId();
                continue;
            }
            if (!forkParallelGatwayId.equals(taskNodeDTO.getForkParallelGatwayId()) ||
                    !joinParallelGatwayId.equals(taskNodeDTO.getJoinParallelGatwayId())) {
                throw new RuntimeException("目标节点不是同一个并行网关");
            }

            if ("".equals(parallelGatewayForkRef)) {
                parallelGatewayForkRef = taskNodeDTO.getParallelGatewayForkRef();
                continue;
            }
            if (parallelGatewayForkRef.equals(taskNodeDTO.getParallelGatewayForkRef())) {
                throw new RuntimeException("目标节点不能为同一个网关中相同分支线上");
            }

        }

        ParallelGatwayDTO forkGateway = forkGatewayMap.get(forkParallelGatwayId);
        int reduceForkSize = forkGateway.getForkSize() - newActivityIds.size();

        if (reduceForkSize < 0) {
            throw new RuntimeException("目标节点数量不能大于并行网关的总分支数量");
        }
        if (reduceForkSize > 0) {
            for (int i = 0; i < reduceForkSize; i++) {
                logger.info("插入网关完成线" + joinParallelGatwayId);
                insertExecutionTest(joinParallelGatwayId, task.getProcessInstanceId(), task.getProcessDefinitionId(), task.getTenantId());
            }
        }

        // 如果该网关是子网关则，还需要处理父网关信息
        ParallelGatwayDTO parentParallelGatwayDTO = forkGateway.getParentParallelGatwayDTO();
        while (parentParallelGatwayDTO != null) {

            for (int i = 0; i < parentParallelGatwayDTO.getForkSize() - 1; i++) {
                logger.info("插入网关完成线" + parentParallelGatwayDTO.getJoinId());
                insertExecutionTest(parentParallelGatwayDTO.getJoinId(), task.getProcessInstanceId(), task.getProcessDefinitionId(), task.getTenantId());
            }

            parentParallelGatwayDTO = parentParallelGatwayDTO.getParentParallelGatwayDTO();
        }

        runtimeService.createChangeActivityStateBuilder().processInstanceId(
                task.getProcessInstanceId())
                .moveSingleActivityIdToActivityIds(task.getTaskDefinitionKey(), newActivityIds)
                .changeState();


    }

    @Test
    public void startProcess() {
        String processKey = "P-TEST3";
        Map<String, Object> variables = new HashMap<>();
        variables.put("aaa", 1);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                processKey, "BUSINESS-KEY010", variables);
        // 50ff0dc9-273e-11eb-b659-f83441e50f2a
        logger.info("流程启动成功 processInstanceId = {}", processInstance.getId());

    }

    @Test
    public void listTodoTask() {
        String instanceId = "575e9c44-2317-11eb-a3f3-f83441e50f2a";
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

        String taskId = "a5e7f8d4-2a52-11eb-ac4a-f83441e50f2a";
        taskService.complete(taskId);

        // taskService.completeTaskWithForm();
        //taskId = "8c32a78b-2a42-11eb-899d-f83441e50f2a";
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
        List<String> ids = Arrays.asList(
                "2c6b9d4e-20ed-11eb-be2f-f83441e50f2a",
                "3e5b0640-2a38-11eb-bf12-f83441e50f2a",
                "50ff0dc9-273e-11eb-b659-f83441e50f2a",
                "567df0b2-2a4b-11eb-94c9-f83441e50f2a");
        ids.forEach(item -> {
            runtimeService.deleteProcessInstance(item, null);
            historyService.deleteHistoricProcessInstance(item);
        });

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


    @Test
    public void testTask() {

        Task task = taskService.createTaskQuery().taskId("b6966469-3923-11eb-9ad9-005056c00001").singleResult();

        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);

        FlowElement flowElement = process.getFlowElement("T6");
        if (flowElement instanceof UserTask) {
            logger.info("getInputDataItem={}", ((UserTask) flowElement).getLoopCharacteristics().getInputDataItem());
            logger.info("getElementVariable={}", ((UserTask) flowElement).getLoopCharacteristics().getElementVariable());
            logger.info("getElementIndexVariable={}", ((UserTask) flowElement).getLoopCharacteristics().getElementIndexVariable());

            logger.info(" {} ", JSONObject.toJSONString(flowElement));
        }

    }

    @Test
    public void getVar() {
        // getCustomProperty();
        String processInstanceId = "b692bad7-3923-11eb-9ad9-005056c00001";
        Object var = runtimeService.getVariable(processInstanceId, "auditUserList2");
        ArrayList<String> collectionValueList = new ArrayList<String>((List<String>) var);
        collectionValueList.add("puser8");

        logger.info(JSONObject.toJSONString(collectionValueList));
    }

    @Test
    public void deleteTask() {
        String taskId = "82454bb3-39c2-11eb-9a8d-005056c00001";
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String executionId = task.getExecutionId();
        logger.info("删除executionId=" + executionId);
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_hi_identitylink  WHERE TASK_ID_ = '" + taskId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_hi_actinst  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_hi_taskinst  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_hi_varinst  WHERE EXECUTION_ID_ = '" + executionId + "'").list();

        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_suspended_job  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_deadletter_job  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_timer_job  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_identitylink  WHERE TASK_ID_ = '" + taskId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_actinst  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_task  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_variable  WHERE EXECUTION_ID_ = '" + executionId + "'").list();
        runtimeService.createNativeExecutionQuery().sql(
                "DELETE FROM act_ru_execution  WHERE ID_ = '" + executionId + "'").list();

    }

    @Test
    public void getExections() {
        String taskId = "60ed1bb6-39f3-11eb-bef7-005056c00001";
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityId(task.getTaskDefinitionKey()).onlyChildExecutions().list();

        logger.info(executions.size()+"");


    }


}