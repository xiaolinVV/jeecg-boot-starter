package org.jeecg.modules.flowable.apithird.business.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;

import java.io.Serializable;
import java.util.Map;

/**
 * @Description: 流程业务扩展表
 * @Author: jeecg-boot
 * @Date:   2021-11-25
 * @Version: V1.0
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="flow_my_business对象", description="流程业务扩展表")
public class FlowMyBusinessDto implements Serializable {
    private static final long serialVersionUID = 1L;

	/**流程定义key 一个key会有多个版本的id*/
	@TableField(exist = false)
    @ApiModelProperty(value = "流程定义key 一个key会有多个版本的id")
    private String processDefinitionKey;
	/**流程定义id 一个流程定义唯一*/
    @TableField(exist = false)
    @ApiModelProperty(value = "流程定义id 一个流程定义唯一")
    private String processDefinitionId;
	/**流程业务实例id 一个流程业务唯一，本表中也唯一*/
    @TableField(exist = false)
    @ApiModelProperty(value = "流程业务实例id 一个流程业务唯一，本表中也唯一")
    private String processInstanceId;
	/**流程业务简要描述*/
    @TableField(exist = false)
    @ApiModelProperty(value = "流程业务简要描述")
    private String title;
	/**业务表id，理论唯一*/
    @TableField(exist = false)
    @ApiModelProperty(value = "业务表id，理论唯一")
    private String dataId;
	/**业务类名，用来获取spring容器里的服务对象*/
    @TableField(exist = false)
    @ApiModelProperty(value = "业务类名，用来获取spring容器里的服务对象")
    private String serviceImplName;
	/**申请人*/
    @TableField(exist = false)
    @ApiModelProperty(value = "申请人")
    private String proposer;
    /**申请人名称*/
    @TableField(exist = false)
    @ApiModelProperty(value = "申请人名称")
    private String proposerName;
    /**申请人部门ID*/
    @TableField(exist = false)
    @ApiModelProperty(value = "申请人部门ID")
    private String proposerDeptId;
    /**申请人部门名称*/
    @TableField(exist = false)
    @ApiModelProperty(value = "申请人部门名称")
    private String proposerDeptName;
	/**流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常*/
    @TableField(exist = false)
    @ApiModelProperty(value = "流程状态说明，有：启动  撤回  驳回  审批中  审批通过  审批异常")
    private String bpmStatus;
    @TableField(exist = false)
    private String bpmStatus_dictText;
	/**当前的节点实例上的Id*/
    @TableField(exist = false)
    @ApiModelProperty(value = "当前的节点Id")
    private String taskId;
	/**当前的节点*/
    @TableField(exist = false)
    @ApiModelProperty(value = "当前的节点")
    private String taskName;
	/**当前的节点定义上的Id*/
    @TableField(exist = false)
    @ApiModelProperty(value = "当前的节点")
    private String taskNameId;
	/**当前的节点可以处理的用户名，为username的集合json字符串*/
    @TableField(exist = false)
    @ApiModelProperty(value = "当前的节点可以处理的用户名")
    private String todoUsers;
	/**处理过的人,为username的集合json字符串*/
    @TableField(exist = false)
    @ApiModelProperty(value = "处理过的人")
    private String doneUsers;
	/**当前任务节点的优先级 流程定义的时候所填*/
    @TableField(exist = false)
    @ApiModelProperty(value = "当前任务节点的优先级 流程定义的时候所填")
    private String priority;
    /**积木报表ID, 可查看当前审批单挂载的单据报表页面*/
    @TableField(exist = false)
    @ApiModelProperty(value = "积木报表ID, 可查看当前审批单挂载的单据报表页面")
    private String jimuReportId;
    /**PC表单组件地址*/
    @TableField(exist = false)
    @ApiModelProperty(value = "PC表单组件地址")
    private String pcFormUrl;
	/**流程变量*/
	@TableField(exist = false)
    private Map<String,Object> values;

    public String getActStatus_dictText() {
        return ActStatus.getTextByVal(this.bpmStatus);
    }
}
