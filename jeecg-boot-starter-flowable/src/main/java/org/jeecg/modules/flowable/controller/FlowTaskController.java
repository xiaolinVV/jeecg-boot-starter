package org.jeecg.modules.flowable.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.UserTask;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.flowable.domain.dto.FlowNextDto;
import org.jeecg.modules.flowable.domain.dto.FlowTaskDto;
import org.jeecg.modules.flowable.domain.dto.FlowViewerDto;
import org.jeecg.modules.flowable.domain.vo.FlowTaskVo;
import org.jeecg.modules.flowable.service.IFlowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * <p>工作流任务管理<p>
 *
 */
@Slf4j
@Api(tags = "工作流流程任务管理")
@RestController
@RequestMapping("/flowable/task")
public class FlowTaskController {

    @Autowired
    private IFlowTaskService flowTaskService;

    @ApiOperation(value = "我发起的流程", response = FlowTaskDto.class)
    @GetMapping(value = "/myProcess")
    public Result<Page<FlowTaskDto>> myProcess(@ApiParam(value = "当前页码", required = true) @RequestParam Integer pageNo,
                                @ApiParam(value = "每页条数", required = true) @RequestParam Integer pageSize) {
        return flowTaskService.myProcess(pageNo, pageSize);
    }

    @ApiOperation(value = "取消申请", response = FlowTaskDto.class)
    @PostMapping(value = "/stopProcess")
    public Result stopProcess(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.stopProcess(flowTaskVo);
    }

    @ApiOperation(value = "撤回流程", response = FlowTaskDto.class)
    @PostMapping(value = "/revokeProcess")
    public Result revokeProcess(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.revokeProcess(flowTaskVo);
    }

    @ApiOperation(value = "获取待办列表", response = FlowTaskDto.class)
    @GetMapping(value = "/todoList")
    public Result<Page<FlowTaskDto>> todoList(@ApiParam(value = "当前页码", required = true) @RequestParam Integer pageNo,
                                              @ApiParam(value = "每页条数", required = true) @RequestParam Integer pageSize,
                                              @RequestParam(required = false) String itemName,
                                              @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd") java.util.Date applyTimeBegin,
                                              @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd")java.util.Date applyTimeEnd,
                                              @RequestParam(required = false) String applyKeshi,
                                              @RequestParam(required = false) String applyPeople) {
        return flowTaskService.todoList(pageNo, pageSize,itemName,applyTimeBegin,applyTimeEnd,applyKeshi,applyPeople);
    }

    @ApiOperation(value = "获取已办任务", response = FlowTaskDto.class)
    @GetMapping(value = "/finishedList")
    public Result<Page<FlowTaskDto>> finishedList(@ApiParam(value = "当前页码", required = true) @RequestParam Integer pageNo,
                                                  @ApiParam(value = "每页条数", required = true) @RequestParam Integer pageSize,
                                                  @RequestParam(required = false) String itemName,
                                                  @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd") java.util.Date applyTimeBegin,
                                                  @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd")java.util.Date applyTimeEnd,
                                                  @RequestParam(required = false) String applyKeshi,
                                                  @RequestParam(required = false) String applyPeople)  {
        return flowTaskService.finishedList(pageNo, pageSize,itemName,applyTimeBegin,applyTimeEnd,applyKeshi,applyPeople);
    }


    @ApiOperation(value = "流程历史流转记录", response = FlowTaskDto.class)
    @GetMapping(value = "/flowRecord")
    public Result flowRecord(String dataId) {
        return Result.OK(flowTaskService.flowRecord(dataId));
    }

    @ApiOperation(value = "查看积木报表单据", response = FlowTaskDto.class)
    @GetMapping(value = "/jimuReportData")
    public Map<String,Object> getJimuReportData(String dataId) {
        return flowTaskService.jimuReportData(dataId);
    }

    @ApiOperation(value = "获取流程变量", response = FlowTaskDto.class)
    @GetMapping(value = "/processVariables/{taskId}")
    public Result processVariables(@ApiParam(value = "流程任务Id")  @PathVariable(value = "taskId") String taskId) {
        return flowTaskService.processVariables(taskId);
    }

    @ApiOperation(value = "审批任务")
    @PostMapping(value = "/complete")
    public Result complete(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.complete(flowTaskVo);
    }
    @ApiOperation(value = "审批任务")
    @PostMapping(value = "/completeByDateId")
    public Result completeByDateId(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.completeByDateId(flowTaskVo);
    }

    @ApiOperation(value = "驳回任务")
    @PostMapping(value = "/reject")
    public Result taskReject(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.taskReject(flowTaskVo);
        return Result.OK();
    }
    @ApiOperation(value = "驳回任务")
    @PostMapping(value = "/taskRejectByDataId")
    public Result taskRejectByDataId(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.taskRejectByDataId(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "退回任务")
    @PostMapping(value = "/return")
    public Result taskReturn(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.taskReturn(flowTaskVo);
        return Result.OK();
    }
    @ApiOperation(value = "退回任务")
    @PostMapping(value = "/taskReturnByDataId")
    public Result taskReturnByDataId(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.taskReturnByDataId(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "获取所有可回退的节点")
    @PostMapping(value = "/returnList")
    public Result<List<UserTask>> findReturnTaskList(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.findReturnTaskList(flowTaskVo);
    }
    @ApiOperation(value = "获取所有可回退的节点")
    @PostMapping(value = "/findReturnTaskListByDataId")
    public Result<List<UserTask>> findReturnTaskListByDataId(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.findReturnTaskListByDataId(flowTaskVo);
    }

    @ApiOperation(value = "删除任务")
    @DeleteMapping(value = "/delete")
    public Result delete(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.deleteTask(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "认领/签收任务")
    @PostMapping(value = "/claim")
    public Result claim(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.claim(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "取消认领/签收任务")
    @PostMapping(value = "/unClaim")
    public Result unClaim(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.unClaim(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "委派任务")
    @PostMapping(value = "/delegate")
    public Result delegate(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.delegateTask(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "转办任务")
    @PostMapping(value = "/assign")
    public Result assign(@RequestBody FlowTaskVo flowTaskVo) {
        flowTaskService.assignTask(flowTaskVo);
        return Result.OK();
    }

    @ApiOperation(value = "获取下一节点")
    @PostMapping(value = "/nextFlowNode")
    public Result<FlowNextDto> getNextFlowNode(@RequestBody FlowTaskVo flowTaskVo) {
        return flowTaskService.getNextFlowNode(flowTaskVo);
    }

    /**
     * 生成流程图
     *
     * @param processId 任务ID
     */
    @RequestMapping("/diagram/{processId}")
    public void genProcessDiagram(HttpServletResponse response,
                                  @PathVariable("processId") String processId) {
        InputStream inputStream =  flowTaskService.diagram(processId);
        OutputStream os = null;
        BufferedImage image = null;
        try {
            image = ImageIO.read(inputStream);
            response.setContentType("image/png");
            os = response.getOutputStream();
            if (image != null) {
                ImageIO.write(image, "png", os);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 生成流程图
     *
     * @param procInsId 任务ID
     */
    @RequestMapping("/flowViewer/{procInsId}")
    public Result<List<FlowViewerDto>> getFlowViewer(@PathVariable("procInsId") String procInsId) {
        return Result.OK(flowTaskService.getFlowViewer(procInsId));
    }
    /**
     * 生成流程图
     *
     * @param dataId 任务数据ID
     */
    @RequestMapping("/flowViewerByDataId/{dataId}")
    public Result<List<FlowViewerDto>> getFlowViewerByDataId(@PathVariable("dataId") String dataId) {
        return Result.OK(flowTaskService.getFlowViewerByDataId(dataId));
    }
}
