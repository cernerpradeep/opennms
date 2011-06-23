/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2011 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2011 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *
 * From the original copyright headers:
 *
 * Copyright (c) 2009+ desmax74
 * Copyright (c) 2009+ The OpenNMS Group, Inc.
 *
 * This program was developed and is maintained by Rocco RIONERO
 * ("the author") and is subject to dual-copyright according to
 * the terms set in "The OpenNMS Project Contributor Agreement".
 *
 * The author can be contacted at the following email address:
 *
 *     Massimiliano Dess&igrave;
 *     desmax74@yahoo.it
 *******************************************************************************/

package org.opennms.acl.factory;

import org.opennms.acl.domain.GenericUser;
import org.opennms.acl.model.UserAuthoritiesDTO;
import org.opennms.acl.service.AuthorityService;
import org.opennms.acl.service.GroupService;
import org.opennms.acl.service.UserService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Acl user factory, retrieve users by id or username
 *
 * @author Massimiliano Dess&igrave; (desmax74@yahoo.it)
 * @since jdk 1.5.0
 * @version $Id: $
 */
@Service("aclUserFactory")
public class AclUserFactoryImpl implements AclUserFactory, InitializingBean {

    /** {@inheritDoc} */
    public GenericUser getAclUserByUsername(String username) {
        UserAuthoritiesDTO userAuthoritiesDTO = userService.getUserWithAuthorities(username);
        return new GenericUser(userAuthoritiesDTO, userService, groupService);
    }

    /** {@inheritDoc} */
    public GenericUser getAclUser(Integer sid) {
        UserAuthoritiesDTO userAuthoritiesDTO = userService.getUserWithAuthoritiesByID(sid);
        return new GenericUser(userAuthoritiesDTO, userService, groupService);
    }

    /**
     * <p>afterPropertiesSet</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void afterPropertiesSet() throws Exception {
        Assert.state(authorityService != null, "authorityService property must be set and cannot be null");
        Assert.state(userService != null, "userService property must be set and cannot be null");
    }

    @Autowired
    private UserService userService;
    @Autowired
    private AuthorityService authorityService;
    @Autowired
    private GroupService groupService;
}
