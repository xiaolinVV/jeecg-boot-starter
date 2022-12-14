package org.jeecg.modules.flowable.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>工作流任务<p>
 *
 */
@Getter
@Setter
@ApiModel("工作流任务相关-返回参数")
public class FlowTaskDto implements Serializable {

    @ApiModelProperty("任务编号")
    private String taskId;

    @ApiModelProperty("任务名称")
    private String taskName;

    @ApiModelProperty("任务Key")
    private String taskDefKey;

    @ApiModelProperty("任务执行人Id")
    private String assigneeId;

    @ApiModelProperty("部门名称")
    private String deptName;

    @ApiModelProperty("流程发起人部门名称")
    private String startDeptName;

    @ApiModelProperty("任务执行人名称")
    private String assigneeName;

    @ApiModelProperty("流程发起人Id")
    private String startUserId;

    @ApiModelProperty("流程发起人名称")
    private String startUserName;

    @ApiModelProperty("流程类型")
    private String category;
    private String category_dictText;

    @ApiModelProperty("流程变量信息")
    private Object procVars;

    @ApiModelProperty("局部变量信息")
    private Object taskLocalVars;

    @ApiModelProperty("流程部署编号")
    private String deployId;

    @ApiModelProperty("流程ID")
    private String procDefId;

    @ApiModelProperty("流程key")
    private String procDefKey;

    @ApiModelProperty("流程定义名称")
    private String procDefName;

    @ApiModelProperty("流程定义内置使用版本")
    private int procDefVersion;

    @ApiModelProperty("流程实例ID")
    private String procInsId;

    @ApiModelProperty("历史流程实例ID")
    private String hisProcInsId;

    @ApiModelProperty("任务耗时")
    private String duration;

    @ApiModelProperty("任务意见")
    private FlowCommentDto comment;

    @ApiModelProperty("候选执行人")
    private String candidate;

    @ApiModelProperty("任务创建时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @ApiModelProperty("任务完成时间")
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    /**流程业务简要描述*/
    @ApiModelProperty(value = "流程业务简要描述")
    private String title;
    /**业务表id，理论唯一*/
    @ApiModelProperty(value = "业务表id，理论唯一")
    private String dataId;

    /**流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常*/
    @ApiModelProperty(value = "流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常")
    private String bpmStatus;

    /**当前的节点可以处理的用户名，为username的集合json字符串*/
    @ApiModelProperty(value = "当前的节点可以处理的用户名")
    private String todoUsers;
    /**处理过的人,为username的集合json字符串*/
    @ApiModelProperty(value = "处理过的人")
    private String doneUsers;

    /**积木报表ID, 可查看当前审批单挂载的单据报表页面*/
    @ApiModelProperty(value = "积木报表ID, 可查看当前审批单挂载的单据报表页面")
    private String jimuReportId;

    /**PC表单组件地址*/
    @ApiModelProperty(value = "PC表单组件地址")
    private String pcFormUrl;
}
