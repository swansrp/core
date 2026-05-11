package com.bidr.td.controller;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.td.dao.entity.TdTagMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/td/tag/mapping")
// Entity and VO are the same type (TdTagMapping) - acceptable for simple CRUD controllers
public class TdTagMappingController extends BaseAdminController<TdTagMapping, TdTagMapping> {
}
