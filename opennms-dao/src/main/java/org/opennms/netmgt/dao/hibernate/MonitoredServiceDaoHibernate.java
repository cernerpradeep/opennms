//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2005 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.                                                            
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//    
// For more information contact: 
//   OpenNMS Licensing       <license@opennms.org>
//   http://www.opennms.org/
//   http://www.opennms.com/
//
// Tab Size = 8

package org.opennms.netmgt.dao.hibernate;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opennms.netmgt.dao.MonitoredServiceDao;
import org.opennms.netmgt.filter.FilterDaoFactory;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.ServiceSelector;
import org.opennms.netmgt.model.OnmsIpInterface.PrimaryType;
/**
 * <p>MonitoredServiceDaoHibernate class.</p>
 *
 * @author david
 * @version $Id: $
 */
public class MonitoredServiceDaoHibernate extends AbstractDaoHibernate<OnmsMonitoredService, Integer>  implements MonitoredServiceDao {

    /**
     * <p>Constructor for MonitoredServiceDaoHibernate.</p>
     */
    public MonitoredServiceDaoHibernate() {
		super(OnmsMonitoredService.class);
	}

	/** {@inheritDoc} */
    @Override
	public List<OnmsMonitoredService> findByType(String type) {
		return find("from OnmsMonitoredService svc where svc.serviceType.name = ?", type);
	}

    /** {@inheritDoc} */
    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddress, String svcName) {
        return findUnique("from OnmsMonitoredService as svc " +
                    "where svc.ipInterface.node.id = ? and svc.ipInterface.ipAddress = ? and svc.serviceType.name = ?",
                   nodeId, ipAddress, svcName);
    }
    
	/** {@inheritDoc} */
    @Override
	public OnmsMonitoredService getPrimaryService(Integer nodeId, String svcName) {
	    return findUnique("from OnmsMonitoredService as svc " +
	                      "where svc.ipInterface.node.id = ? and svc.ipInterface.isSnmpPrimary= ? and svc.serviceType.name = ?",
	                     nodeId, PrimaryType.PRIMARY, svcName);
	}

	/** {@inheritDoc} */
	@Override
	public OnmsMonitoredService get(Integer nodeId, String ipAddr, Integer ifIndex, Integer serviceId) {
		return findUnique("from OnmsMonitoredService as svc " +
			    "where svc.ipInterface.node.id = ? and svc.ipInterface.ipAddress = ? and svc.ipInterface.snmpInterface.ifIndex = ? and svc.serviceType.id = ?",
			   nodeId, ipAddr, ifIndex, serviceId);
	}

    /** {@inheritDoc} */
    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddr, Integer ifIndex, Integer serviceId) {
        return findUnique("from OnmsMonitoredService as svc " +
                "where svc.ipInterface.node.id = ? and svc.ipInterface.ipAddress = ? and svc.ipInterface.snmpInterface.ifIndex = ? and svc.serviceType.id = ?",
               nodeId, ipAddr, ifIndex, serviceId);
    }

    /** {@inheritDoc} */
    @Override
    public List<OnmsMonitoredService> findMatchingServices(ServiceSelector selector) {
        Set<InetAddress> matchingAddrs = new HashSet<InetAddress>(FilterDaoFactory.getInstance().getActiveIPAddressList(selector.getFilterRule()));
        Set<String> matchingSvcs = new HashSet<String>(selector.getServiceNames());
        
        List<OnmsMonitoredService> matchingServices = new LinkedList<OnmsMonitoredService>();
        Collection<OnmsMonitoredService> services = findActive();
        for (OnmsMonitoredService svc : services) {
            if ((matchingSvcs.contains(svc.getServiceName()) || matchingSvcs.isEmpty()) &&
                matchingAddrs.contains(svc.getIpAddress())) {
                
                matchingServices.add(svc);
            }
            
        }
        
        
        return matchingServices;
    }

    private Collection<OnmsMonitoredService> findActive() {
        return find("select distinct svc from OnmsMonitoredService as svc " +
        		"left join fetch svc.serviceType " +
        		"left join fetch svc.ipInterface as ip " +
        		"left join fetch ip.node as node " +
        		"left join fetch node.assetRecord " +
        		"where (svc.status is null or svc.status not in ('F','U','D'))");
    }

    /** {@inheritDoc} */
    @Override
    public Set<OnmsMonitoredService> findByApplication(OnmsApplication application) {
        return application.getMonitoredServices();
    }

}
