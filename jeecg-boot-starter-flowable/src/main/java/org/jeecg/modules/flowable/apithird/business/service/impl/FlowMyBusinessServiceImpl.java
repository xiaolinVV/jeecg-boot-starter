package org.jeecg.modules.flowable.apithird.business.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.mapper.FlowMyBusinessMapper;
import org.jeecg.modules.flowable.apithird.business.service.IFlowMyBusinessService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 流程业务扩展表
 * @Author: jeecg-boot
 * @Date:   2021-11-25
 * @Version: V1.0
 */
@Service
public class FlowMyBusinessServiceImpl extends ServiceImpl<FlowMyBusinessMapper, FlowMyBusiness> implements IFlowMyBusinessService {

    public FlowMyBusiness getByDataId(String dataId) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId,dataId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = this.getOne(flowMyBusinessLambdaQueryWrapper);
        return business;
    }


    public FlowMyBusiness getByProcessInstanceId(String processInstanceId){
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getProcessInstanceId,processInstanceId);
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = this.getOne(flowMyBusinessLambdaQueryWrapper);
        return business;
    }

    public Map<String,FlowMyBusiness> getByProcessInstanceIds(List<String> processInstanceIds){
        if (CollUtil.isEmpty(processInstanceIds)) {
            return MapUtil.empty();
        }
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.in(FlowMyBusiness::getProcessInstanceId,processInstanceIds);
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        return list(flowMyBusinessLambdaQueryWrapper).stream().collect(Collectors.toMap(FlowMyBusiness::getProcessInstanceId, e -> e));
    }

    public List<String> getProcessInstanceIds(String itemName,
                                              java.util.Date applyTimeBegin,
                                              java.util.Date applyTimeEnd,
                                              String proposerDeptName,
                                              String applyPeople) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.like(StrUtil.isNotBlank(itemName), FlowMyBusiness::getTitle, itemName);
        flowMyBusinessLambdaQueryWrapper.like(StrUtil.isNotBlank(proposerDeptName), FlowMyBusiness::getProposerDeptName, proposerDeptName);
        flowMyBusinessLambdaQueryWrapper.like(StrUtil.isNotBlank(applyPeople), FlowMyBusiness::getProposerName, applyPeople);
        if (applyTimeBegin != null && applyTimeEnd != null) {
            flowMyBusinessLambdaQueryWrapper.ge(FlowMyBusiness::getCreateTime, applyTimeBegin);
            flowMyBusinessLambdaQueryWrapper.le(FlowMyBusiness::getCreateTime, applyTimeEnd);
        }
        List<FlowMyBusiness> list = list(flowMyBusinessLambdaQueryWrapper);
        return list.stream().map(FlowMyBusiness::getProcessInstanceId).collect(Collectors.toList());
    }

}
