package org.jeecg.modules.flowable.apithird.entity;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;

/**
 * @author PanMeiCheng
 * @version 1.0
 * @date 2021/11/26
 */
public enum ActStatus {
    //启动 撤回 驳回 审批中 审批通过 审批异常
    //本流程不应有启动状态，启动即进入审批，第一个节点就是发起人节点，未方便业务区分，设定为“启动”状态
    waitStart("0", "草稿"),
    start("1", "启动"),
    recall("2", "撤回"),
    reject("3", "驳回"),
    doing("4", "审批中"),
    pass("5", "审批通过"),
    err("6", "审批异常");

    /**
     * 流程状态
     */
    private String value;

    /**
     * 流程状态说明
     */
    private String text;

    ActStatus(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    /**
     * 根据 value 查找对应文本
     *
     * @param value value 值
     * @return 对应文本
     */
    public static String getTextByVal(String value) {
        return Arrays.stream(values()).filter(actStatus -> StrUtil.equals(actStatus.getValue(), value)).map(ActStatus::getText).findFirst().orElse("");
    }
}
