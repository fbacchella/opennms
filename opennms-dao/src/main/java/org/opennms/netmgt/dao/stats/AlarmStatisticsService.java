package org.opennms.netmgt.dao.stats;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.opennms.netmgt.dao.AlarmDao;
import org.opennms.netmgt.dao.OnmsDao;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class AlarmStatisticsService extends AbstractBaseStatisticsService<OnmsAlarm> {

    @Autowired AlarmDao m_alarmDao;

    @Override
    public OnmsDao<OnmsAlarm, Integer> getDao() {
        return m_alarmDao;
    }

    @Transactional
    public int getAcknowledgedCount(final OnmsCriteria criteria) {
        criteria.add(Restrictions.isNotNull("alarmAckUser"));
        return m_alarmDao.countMatching(criteria);
    }

    @Transactional
    public OnmsAlarm getAcknowledged(final OnmsCriteria criteria) {
        criteria.add(Restrictions.isNotNull("alarmAckUser"));
        criteria.setMaxResults(1);
        final List<OnmsAlarm> alarms = m_alarmDao.findMatching(criteria);
        if (alarms.size() == 0) return null;
        return alarms.get(0);
    }

    @Transactional
    public OnmsAlarm getUnacknowledged(final OnmsCriteria criteria) {
        criteria.add(Restrictions.isNull("alarmAckUser"));
        criteria.setMaxResults(1);
        final List<OnmsAlarm> alarms = m_alarmDao.findMatching(criteria);
        if (alarms.size() == 0) return null;
        return alarms.get(0);
    }

}