package org.jeecg.modules.flowable.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.io.IOUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.service.impl.FlowMyBusinessServiceImpl;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;
import org.jeecg.modules.flowable.apithird.entity.SysUser;
import org.jeecg.modules.flowable.apithird.service.FlowCallBackServiceI;
import org.jeecg.modules.flowable.apithird.service.IFlowThirdService;
import org.jeecg.modules.flowable.common.constant.ProcessConstants;
import org.jeecg.modules.flowable.common.enums.FlowComment;
import org.jeecg.modules.flowable.domain.dto.FlowNextDto;
import org.jeecg.modules.flowable.domain.dto.FlowProcDefDto;
import org.jeecg.modules.flowable.factory.FlowServiceFactory;
import org.jeecg.modules.flowable.service.IFlowDefinitionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ????????????
 *
 */
@Service
public class FlowDefinitionServiceImpl extends FlowServiceFactory implements IFlowDefinitionService {
    @Autowired
    IFlowThirdService iFlowThirdService;
    @Autowired
    FlowMyBusinessServiceImpl flowMyBusinessService;
    @Autowired
    FlowTaskServiceImpl flowTaskService;

    private static final String BPMN_FILE_SUFFIX = ".bpmn";

    @Override
    public boolean exist(String processDefinitionKey) {
        ProcessDefinitionQuery processDefinitionQuery
                = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processDefinitionKey);
        long count = processDefinitionQuery.count();
        return count > 0 ? true : false;
    }


    /**
     * ??????????????????
     *
     * @param pageNum  ????????????
     * @param pageSize ????????????
     * @param flowProcDefDto
     * @return ??????????????????????????????
     */
    @Override
    public Page<FlowProcDefDto> list(Integer pageNum, Integer pageSize, FlowProcDefDto flowProcDefDto) {
        Page<FlowProcDefDto> page = new Page<>();
        // ??????????????????????????????
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
        processDefinitionQuery
                //.processDefinitionId("cs:5:15e953ed-4d09-11ec-85b8-e884a5deddfc")
                .orderByProcessDefinitionKey().asc().orderByProcessDefinitionVersion().desc();
        /*=====??????=====*/
        if (StrUtil.isNotBlank(flowProcDefDto.getName())){
            processDefinitionQuery.processDefinitionNameLike("%"+flowProcDefDto.getName()+"%");
        }
        if (StrUtil.isNotBlank(flowProcDefDto.getCategory())){
            processDefinitionQuery.processDefinitionCategory(flowProcDefDto.getCategory());
        }
        if (flowProcDefDto.getSuspensionState() == 1){
            processDefinitionQuery.active();
        }
        if (StrUtil.isNotBlank(flowProcDefDto.getKey())){
            processDefinitionQuery.processDefinitionKey(flowProcDefDto.getKey());
        }
        if (flowProcDefDto.getIsLastVersion() == 1){
            processDefinitionQuery.latestVersion();
        }
        /*============*/
        page.setTotal(processDefinitionQuery.count());
        List<ProcessDefinition> processDefinitionList = processDefinitionQuery.listPage((pageNum - 1) * pageSize, pageSize);

        List<FlowProcDefDto> dataList = new ArrayList<>();
        for (ProcessDefinition processDefinition : processDefinitionList) {
            String deploymentId = processDefinition.getDeploymentId();
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
            FlowProcDefDto reProcDef = new FlowProcDefDto();
            BeanUtils.copyProperties(processDefinition, reProcDef);
            // ??????????????????
            reProcDef.setDeploymentTime(deployment.getDeploymentTime());
            dataList.add(reProcDef);
        }
        page.setRecords(dataList);
        return page;
    }


    /**
     * ??????????????????
     *
     * @param name
     * @param category
     * @param in
     */
    @Override
    public void importFile(String name, String category, InputStream in) {
        Deployment deploy = repositoryService.createDeployment().addInputStream(name + BPMN_FILE_SUFFIX, in).name(name).category(category).deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deploy.getId()).singleResult();
        repositoryService.setProcessDefinitionCategory(definition.getId(), category);

    }

    /**
     * ??????xml
     *
     * @param deployId
     * @return
     */
    @Override
    public Result readXml(String deployId) throws IOException {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName());
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        return Result.OK("", result);
    }

    @Override
    public Result readXmlByDataId(String dataId) throws IOException {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId,dataId)
        ;
        //???????????????????????????????????????FlowCommonService.initActBusiness????????????????????????
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(business.getProcessDefinitionId()).singleResult();
        InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName());
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        return Result.OK("", result);
    }

    /**
     * ??????xml ????????????Id
     *
     * @param dataId
     * @return
     */
    @Override
    public InputStream readImageByDataId(String dataId) {
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);

        String processId = business.getProcessInstanceId();
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
        //??????????????? ????????????
        if (pi == null) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(business.getProcessDefinitionId()).singleResult();
            return this.readImage(processDefinition.getDeploymentId());
        }

        List<HistoricActivityInstance> historyProcess = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processId).list();
        List<String> activityIds = new ArrayList<>();
        List<String> flows = new ArrayList<>();
        //???????????????
        BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
        for (HistoricActivityInstance hi : historyProcess) {
            String activityType = hi.getActivityType();
            if (activityType.equals("sequenceFlow") || activityType.equals("exclusiveGateway")) {
                flows.add(hi.getActivityId());
            } else if (activityType.equals("userTask") || activityType.equals("startEvent")) {
                activityIds.add(hi.getActivityId());
            }
        }
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processId).list();
        for (Task task : tasks) {
            activityIds.add(task.getTaskDefinitionKey());
        }
        ProcessEngineConfiguration engConf = processEngine.getProcessEngineConfiguration();
        //???????????????????????????
        ProcessDiagramGenerator processDiagramGenerator = engConf.getProcessDiagramGenerator();
        InputStream in = processDiagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows, engConf.getActivityFontName(), engConf.getLabelFontName(), engConf.getAnnotationFontName(), engConf.getClassLoader(), 1.0, true);
        return in;
    }
    /**
     * ??????xml
     *
     * @param deployId
     * @return
     */
    @Override
    public InputStream readImage(String deployId) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        //???????????????
        DefaultProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        //???????????????
        return diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                Collections.emptyList(),
                Collections.emptyList(),
                "??????",
                "??????",
                "??????",
                null,
                1.0,
                false);

    }

    /**
     * ??????????????????ID??????????????????
     *
     * @param procDefKey ????????????Id
     * @param variables ????????????
     * @return
     */
    @Override
    public Result startProcessInstanceByKey(String procDefKey, Map<String, Object> variables) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(procDefKey)
                .latestVersion().singleResult();
        return startProcessInstanceById(processDefinition.getId(),variables);
    }
    /**
     * ??????????????????ID??????????????????
     *
     * @param procDefId ????????????Id
     * @param variables ????????????
     * @return
     */
    @Override
    @Transactional
    public Result startProcessInstanceById(String procDefId, Map<String, Object> variables) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(procDefId)
                    .singleResult();
            if (Objects.nonNull(processDefinition) && processDefinition.isSuspended()) {
                return Result.error("??????????????????,??????????????????");
            }
//           variables.put("skip", true);
//           variables.put(ProcessConstants.FLOWABLE_SKIP_EXPRESSION_ENABLED, true);
            // ?????????????????????Id????????????
            SysUser sysUser = iFlowThirdService.getLoginUser();
            identityService.setAuthenticatedUserId(sysUser.getUsername());
            variables.put(ProcessConstants.PROCESS_INITIATOR, sysUser.getUsername());
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(procDefId, variables);
            // ?????????????????????????????????????????????????????????
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
            if (Objects.nonNull(task)) {
                taskService.addComment(task.getId(), processInstance.getProcessInstanceId(), FlowComment.NORMAL.getType(), sysUser.getRealname() + "??????????????????");
                taskService.setAssignee(task.getId(), sysUser.getUsername());
                //taskService.complete(task.getId(), variables);
            }
        /*======================todo ????????????  ??????????????????????????????======================*/
        //????????????id
        String dataId = variables.get("dataId").toString();
        //???????????????????????????????????????FlowCommonService.initActBusiness????????????????????????
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
        //????????????
        FlowNextDto nextFlowNode = flowTaskService.getNextFlowNode(task.getId(), variables);
        taskService.complete(task.getId(), variables);
        //?????????????????????  ?????????????????????list,?????????????????????  ???????????????Key????????????
        //Task task2 = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
        List<Task> task2List = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().list();
        Task task2 = null;
        if(task2List.size()>0) task2 = task2List.get(0);
        String doneUsers = business.getDoneUsers();
        // ?????????????????????
        JSONArray doneUserList = new JSONArray();
        if (StrUtil.isNotBlank(doneUsers)){
            doneUserList = JSON.parseArray(doneUsers);
        }
        if (!doneUserList.contains(sysUser.getUsername())){
            doneUserList.add(sysUser.getUsername());
        }

        //??????ID?????????
        String proposerDeptName = String.join(",", iFlowThirdService.getDepartNamesByUsername(sysUser.getUsername()));
        String proposerDeptId = String.join(",", iFlowThirdService.getDepartIdsByUsername(sysUser.getUsername()));

        if (nextFlowNode!=null){
            //**??????????????????
            UserTask nextTask = nextFlowNode.getUserTask();
            //????????????????????????????????????
            List<SysUser> nextFlowNodeUserList = nextFlowNode.getUserList();

            List<String> collect_username = nextFlowNodeUserList.stream().map(SysUser::getUsername).collect(Collectors.toList());
            //spring????????????
            String serviceImplName = business.getServiceImplName();
            FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
            List<String> beforeParamsCandidateUsernames = flowCallBackService.flowCandidateUsernamesOfTask(task2.getTaskDefinitionKey(), variables);

            //??????????????????????????? ??????
            List<String> candidateUsers = StrUtil.split(Convert.toStr(variables.get("candidateUsers")), StrUtil.C_COMMA);
            beforeParamsCandidateUsernames =  CollUtil.isNotEmpty(candidateUsers) ? candidateUsers : beforeParamsCandidateUsernames;

            if (CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                // ???????????????
                for (Task task2One : task2List) {
                    for (String oldUser : collect_username) {
                        taskService.deleteCandidateUser(task2One.getId(),oldUser);
                    }
                }
                // ????????????????????????????????????
                for (Task task2One : task2List) {
                    for (String newUser : beforeParamsCandidateUsernames) {
                        taskService.addCandidateUser(task2One.getId(),newUser);
                    }
                }
                collect_username = beforeParamsCandidateUsernames;
            }

            business.setProcessDefinitionId(procDefId)
                    .setProcessInstanceId(processInstance.getProcessInstanceId())
                    .setBpmStatus(ActStatus.start.getValue())
                    .setProposer(sysUser.getUsername())
                    .setProposerName(sysUser.getRealname())
                    .setProposerDeptName(proposerDeptName)
                    .setProposerDeptId(proposerDeptId)
                    .setTaskId(task2.getId())
                    .setTaskName(nextTask.getName())
                    .setTaskNameId(nextTask.getId())
                    .setPriority(nextTask.getPriority())
                    .setDoneUsers(doneUserList.toJSONString())
                    .setTodoUsers(JSON.toJSONString(collect_username))
            ;
        } else {
        //    **?????????????????????????????????????????????
            business.setProcessDefinitionId(procDefId)
                    .setProcessInstanceId(processInstance.getProcessInstanceId())
                    .setBpmStatus(ActStatus.pass.getValue())
                    .setProposer(sysUser.getUsername())
                    .setProposerName(sysUser.getRealname())
                    .setProposerDeptName(proposerDeptName)
                    .setProposerDeptId(proposerDeptId)
                    .setProposerDeptName(proposerDeptName)
                    .setDoneUsers(doneUserList.toJSONString())
            ;
        }
        flowMyBusinessService.updateById(business);
        //spring????????????
        String serviceImplName = business.getServiceImplName();
        FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
        // ??????????????????????????????????????????
        business.setValues(variables);
        if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);
        return Result.OK("??????????????????");
    }

    @Override
    public Result startProcessInstanceByDataId(String dataId, Map<String, Object> variables) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId, dataId)
        ;
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        if (business==null){
            return Result.error("?????????dataId???"+dataId);
        }
        if (StrUtil.isNotBlank(business.getProcessDefinitionId())){
            return this.startProcessInstanceById(business.getProcessDefinitionId(),variables);
        }
        return this.startProcessInstanceByKey(business.getProcessDefinitionKey(),variables);
    }


    /**
     * ???????????????????????????
     *
     * @param state    ?????? ??????1 ??????2
     * @param deployId ????????????ID
     */
    @Override
    public void updateState(Integer state, String deployId) {
        ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        // ??????
        if (state == 1) {
            repositoryService.activateProcessDefinitionById(procDef.getId(), true, null);
        }
        // ??????
        if (state == 2) {
            repositoryService.suspendProcessDefinitionById(procDef.getId(), true, null);
        }
    }


    /**
     * ??????????????????
     *
     * @param deployId ????????????ID act_ge_bytearray ?????? deployment_id???
     */
    @Override
    public void delete(String deployId) {
        // true ?????????????????? ,?????????????????????????????????????????????
        repositoryService.deleteDeployment(deployId, true);
    }


}
