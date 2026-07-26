package com.schoolai.common;

import com.schoolai.common.page.PageResult;

import java.util.List;

public class BaseController {

    protected <T> Result<T> success(T data) {
        return Result.success(data);
    }

    protected Result<Void> success() {
        return Result.success();
    }

    protected Result<Void> error(String message) {
        return Result.error(message);
    }

    protected Result<Void> error(int code, String message) {
        return Result.error(code, message);
    }

    protected <T> Result<PageResult<T>> successPage(long total, List<T> rows) {
        return Result.success(new PageResult<>(total, rows));
    }
}