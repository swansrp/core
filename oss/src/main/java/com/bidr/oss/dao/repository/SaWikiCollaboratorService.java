package com.bidr.oss.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.mapper.SaWikiCollaboratorMapper;
import org.springframework.stereotype.Service;

/**
 * Wiki协作者Repository Service
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
public class SaWikiCollaboratorService extends BaseSqlRepo<SaWikiCollaboratorMapper, SaWikiCollaborator> {
}
