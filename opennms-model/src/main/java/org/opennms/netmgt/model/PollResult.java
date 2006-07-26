package org.opennms.netmgt.model;



public class PollResult {
	
	private Integer m_id;
	private DemandPoll m_demandPoll;
	private OnmsMonitoredService m_monitoredService;
	private PollStatus m_status;
	
	public PollResult() {
		
	}
	
	public PollResult(int id) {
		m_id = id;
	}
	
	public Integer getId() {
		return m_id;
	}

	public void setId(int id) {
		m_id = id;
	}

	public OnmsMonitoredService getMonitoredService() {
		return m_monitoredService;
	}

	public void setMonitoredService(OnmsMonitoredService monitoredService) {
		m_monitoredService = monitoredService;
	}

	public PollStatus getStatus() {
		return m_status;
	}

	public void setStatus(PollStatus status) {
		m_status = status;
	}

	public DemandPoll getDemandPoll() {
		return m_demandPoll;
	}

	public void setDemandPoll(DemandPoll poll) {
		this.m_demandPoll = poll;
	}	

}
