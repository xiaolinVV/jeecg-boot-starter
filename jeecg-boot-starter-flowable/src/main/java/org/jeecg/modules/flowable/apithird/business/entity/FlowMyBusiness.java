package org.jeecg.modules.flowable.apithird.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * @Description: 流程业务扩展表
 * @Author: jeecg-boot
 * @Date:   2021-11-25
 * @Version: V1.0
 */
@Data
@TableName("flow_my_business")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="flow_my_business对象", description="流程业务扩展表")
public class FlowMyBusiness implements Serializable {
    private static final long serialVersionUID = 1L;

	/**主键ID*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "主键ID")
    private String id;
	/**创建人*/
    @ApiModelProperty(value = "创建人")
    private String createBy;
	/**创建时间*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
	/**修改人*/
    @ApiModelProperty(value = "修改人")
    private String updateBy;
	/**修改时间*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "修改时间")
    private Date updateTime;
	/**流程定义key 一个key会有多个版本的id*/
    @ApiModelProperty(value = "流程定义key 一个key会有多个版本的id")
    private String processDefinitionKey;
	/**流程定义id 一个流程定义唯一*/
    @ApiModelProperty(value = "流程定义id 一个流程定义唯一")
    private String processDefinitionId;
	/**流程业务实例id 一个流程业务唯一，本表中也唯一*/
    @ApiModelProperty(value = "流程业务实例id 一个流程业务唯一，本表中也唯一")
    private String processInstanceId;
	/**流程业务简要描述*/
    @ApiModelProperty(value = "流程业务简要描述")
    private String title;
	/**业务表id，理论唯一*/
    @ApiModelProperty(value = "业务表id，理论唯一")
    private String dataId;
	/**业务类名，用来获取spring容器里的服务对象*/
    @ApiModelProperty(value = "业务类名，用来获取spring容器里的服务对象")
    private String serviceImplName;
	/**申请人*/
    @ApiModelProperty(value = "申请人")
    private String proposer;
	/**申请人名称*/
    @ApiModelProperty(value = "申请人名称")
    private String proposerName;
	/**申请人部门ID*/
    @ApiModelProperty(value = "申请人部门ID")
    private String proposerDeptId;
	/**申请人部门名称*/
    @ApiModelProperty(value = "申请人部门名称")
    private String proposerDeptName;
	/**流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常*/
    @ApiModelProperty(value = "流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常")
    private String bpmStatus;
	/**当前的节点实例上的Id*/
    @ApiModelProperty(value = "当前的节点Id")
    private String taskId;
	/**当前的节点*/
    @ApiModelProperty(value = "当前的节点")
    private String taskName;
	/**当前的节点定义上的Id*/
    @ApiModelProperty(value = "当前的节点")
    private String taskNameId;
	/**当前的节点可以处理的用户名，为username的集合json字符串*/
    @ApiModelProperty(value = "当前的节点可以处理的用户名")
    private String todoUsers;
	/**处理过的人,为username的集合json字符串*/
    @ApiModelProperty(value = "处理过的人")
    private String doneUsers;
	/**当前任务节点的优先级 流程定义的时候所填*/
    @ApiModelProperty(value = "当前任务节点的优先级 流程定义的时候所填")
    private String priority;
	/**积木报表ID, 可查看当前审批单挂载的单据报表页面*/
    @ApiModelProperty(value = "积木报表ID, 可查看当前审批单挂载的单据报表页面")
    private String jimuReportId;
	/**PC表单组件地址*/
    @ApiModelProperty(value = "PC表单组件地址")
    private String pcFormUrl;
	/**流程变量*/
	@TableField(exist = false)
    private Map<String,Object> values;
}
