package com.bidr.task;

/**
 * 任务状态（全任务类型共用，落 {@code sa_async_task.status}）。
 *
 * @author sharp
 */
public enum AsyncTaskStatus {

    /** 待执行 */
    PENDING,

    /** 执行中（被某实例抢占持有租约） */
    RUNNING,

    /** 成功（终态） */
    SUCCESS,

    /** 失败（可重试，退避后再来；观察者不发这一档） */
    FAILED,

    /** 死亡（重试耗尽或不可重试，需人工介入；观察者必发这一档） */
    DEAD;

    public static boolean isTerminal(String status) {
        return SUCCESS.name().equals(status) || DEAD.name().equals(status);
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == DEAD;
    }
}
