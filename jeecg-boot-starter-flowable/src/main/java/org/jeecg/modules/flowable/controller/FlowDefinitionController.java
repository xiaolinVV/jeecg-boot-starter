package org.jeecg.modules.flowable.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.flowable.apithird.entity.SysCategory;
import org.jeecg.modules.flowable.apithird.entity.SysRole;
import org.jeecg.modules.flowable.apithird.entity.SysUser;
import org.jeecg.modules.flowable.apithird.service.IFlowThirdService;
import org.jeecg.modules.flowable.domain.dto.FlowProcDefDto;
import org.jeecg.modules.flowable.domain.dto.FlowSaveXmlVo;
import org.jeecg.modules.flowable.service.IFlowDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 工作流程定义
 * </p>
 *
 */
@Slf4j
@Api(tags = "流程定义")
@RestController
@RequestMapping("/flowable/definition")
public class FlowDefinitionController {

    @Autowired
    private IFlowDefinitionService flowDefinitionService;

    @Autowired
    private IFlowThirdService iFlowThirdService;



    @GetMapping(value = "/list")
    @ApiOperation(value = "流程定义列表", response = FlowProcDefDto.class)
    public Result<Page<FlowProcDefDto>> list(@ApiParam(value = "当前页码", required = true) @RequestParam Integer pageNum,
                                             @ApiParam(value = "每页条数", required = true) @RequestParam Integer pageSize,
                                             FlowProcDefDto flowProcDefDto
    ) {
        return Result.OK(flowDefinitionService.list(pageNum, pageSize,flowProcDefDto));
    }


    @ApiOperation(value = "导入流程文件", notes = "上传bpmn20的xml文件")
    @PostMapping("/import")
    public Result importFile(@RequestParam(required = false) String name,
                                 @RequestParam(required = false) String category,
                                 MultipartFile file) {
        InputStream in = null;
        try {
            in = file.getInputStream();
            flowDefinitionService.importFile(name, category, in);
        } catch (Exception e) {
            log.error("导入失败:", e);
            return Result.OK(e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("关闭输入流出错", e);
            }
        }

        return Result.OK("导入成功");
    }


    @ApiOperation(value = "读取xml文件")
    @GetMapping("/readXml/{deployId}")
    public Result readXml(@ApiParam(value = "流程定义id") @PathVariable(value = "deployId") String deployId) {
        try {
            return flowDefinitionService.readXml(deployId);
        } catch (Exception e) {
            return Result.error("加载xml文件异常");
        }

    }
    @ApiOperation(value = "读取xml文件")
    @GetMapping("/readXmlByDataId/{dataId}")
    public Result readXmlByDataId(@ApiParam(value = "流程定义id") @PathVariable(value = "dataId") String dataId) {
        try {
            return flowDefinitionService.readXmlByDataId(dataId);
        } catch (Exception e) {
            return Result.error("加载xml文件异常");
        }

    }

    @ApiOperation(value = "读取图片文件")
    @GetMapping("/readImage/{deployId}")
    public void readImage(@ApiParam(value = "流程定义id") @PathVariable(value = "deployId") String deployId, HttpServletResponse response) {
        OutputStream os = null;
        BufferedImage image = null;
        try {
            image = ImageIO.read(flowDefinitionService.readImage(deployId));
            response.setContentType("image/png");
            os = response.getOutputStream();
            if (image != null) {
                ImageIO.write(image, "png", os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
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
    @ApiOperation(value = "读取图片文件")
    @GetMapping("/readImageByDataId/{dataId}")
    public void readImageByDataId(@ApiParam(value = "流程数据业务id") @PathVariable(value = "dataId") String dataId, HttpServletResponse response) {
        OutputStream os = null;
        BufferedImage image = null;
        try {
            image = ImageIO.read(flowDefinitionService.readImageByDataId(dataId));
            response.setContentType("image/png");
            os = response.getOutputStream();
            if (image != null) {
                ImageIO.write(image, "png", os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
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


    @ApiOperation(value = "保存流程设计器内的xml文件")
    @PostMapping("/save")
    public Result save(@RequestBody FlowSaveXmlVo vo) {
        InputStream in = null;
        try {
            in = new ByteArrayInputStream(vo.getXml().getBytes(StandardCharsets.UTF_8));
            flowDefinitionService.importFile(vo.getName(), vo.getCategory(), in);
        } catch (Exception e) {
            log.error("导入失败:", e);
            return Result.OK(e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("关闭输入流出错", e);
            }
        }

        return Result.OK("导入成功");
    }


    @ApiOperation(value = "根据流程定义id启动流程实例")
    @PostMapping("/startByProcDefId/{procDefId}")
    public Result startByProcDefId(@ApiParam(value = "流程定义id") @PathVariable(value = "procDefId") String procDefId,
                        @ApiParam(value = "变量集合,json对象") @RequestBody Map<String, Object> variables) {
        return flowDefinitionService.startProcessInstanceById(procDefId, variables);

    }
    @ApiOperation(value = "根据流程定义key启动流程实例")
    @PostMapping("/startByProcDefKey/{procDefKey}")
    public Result startByProcDefKey(@ApiParam(value = "流程定义id") @PathVariable(value = "procDefKey") String procDefKey,
                        @ApiParam(value = "变量集合,json对象") @RequestBody Map<String, Object> variables) {
        return flowDefinitionService.startProcessInstanceByKey(procDefKey, variables);

    }
    @ApiOperation(value = "根据数据Id启动流程实例")
    @PostMapping("/startByDataId/{dataId}")
    public Result startByDataId(@ApiParam(value = "流程定义id") @PathVariable(value = "dataId") String dataId,
                        @ApiParam(value = "变量集合,json对象") @RequestBody Map<String, Object> variables) {
        variables.put("dataId",dataId);
        return flowDefinitionService.startProcessInstanceByDataId(dataId, variables);

    }

    @ApiOperation(value = "激活或挂起流程定义")
    @PutMapping(value = "/updateState")
    public Result updateState(@ApiParam(value = "1:激活,2:挂起", required = true) @RequestParam Integer state,
                                  @ApiParam(value = "流程部署ID", required = true) @RequestParam String deployId) {
        flowDefinitionService.updateState(state, deployId);
        return Result.OK("操作成功");
    }

    @ApiOperation(value = "删除流程")
    @DeleteMapping(value = "/delete")
    public Result delete(@ApiParam(value = "流程部署ID", required = true) @RequestParam String deployId) {
        flowDefinitionService.delete(deployId);
        return Result.OK();
    }

    @ApiOperation(value = "指定流程办理人员列表")
    @GetMapping("/userList")
    public Result<List<SysUser>> userList(SysUser user) {
        List<SysUser> list = iFlowThirdService.getAllUser();
        return Result.OK(list);
    }

    @ApiOperation(value = "指定流程办理组列表")
    @GetMapping("/roleList")
    public Result<List<SysRole>> roleList(SysRole role) {
        List<SysRole> list = iFlowThirdService.getAllRole();
        return Result.OK(list);
    }
    @ApiOperation(value = "指定流程办理组列表")
    @GetMapping("/categoryList")
    public Result<List<SysCategory>> categoryList(SysCategory category) {
        List<SysCategory> list = iFlowThirdService.getAllCategory();
        return Result.OK(list);
    }

}
