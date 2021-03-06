/*
* RHQ Management Platform
* Copyright (C) 2005-2008 Red Hat, Inc.
* All rights reserved.
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License, version 2, as
* published by the Free Software Foundation, and/or the GNU Lesser
* General Public License, version 2.1, also as published by the Free
* Software Foundation.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License and the GNU Lesser General Public License
* for more details.
*
* You should have received a copy of the GNU General Public License
* and the GNU Lesser General Public License along with this program;
* if not, write to the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/
package org.rhq.core.system;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;

import org.hyperic.sigar.NetFlags;
import org.hyperic.sigar.NetStat;
import org.hyperic.sigar.NetConnection;

/**
 * @author Greg Hinkle
 */
public class NetworkStats {

    protected int[] tcpStates;
    protected int tcpInboundTotal, tcpOutboundTotal;
    protected int allInboundTotal, allOutboundTotal;
    protected long sendQueue;
    protected long receiveQueue;

    public NetworkStats(NetStat interfaceStat) {
        refresh(interfaceStat);
    }

    public NetworkStats(NetConnection[] connectionList) {
        refresh(connectionList);
    }

    public void refresh(NetStat interfaceStat) {
        this.tcpStates = interfaceStat.getTcpStates();
        this.tcpInboundTotal = interfaceStat.getTcpInboundTotal();
        this.tcpOutboundTotal = interfaceStat.getTcpOutboundTotal();
        this.allInboundTotal = interfaceStat.getAllInboundTotal();
        this.allOutboundTotal = interfaceStat.getAllOutboundTotal();
    }

    public void refresh(NetConnection[] connectionList) {
        this.tcpStates = new int[14];
        allOutboundTotal = connectionList.length;
        tcpOutboundTotal = connectionList.length;
        this.sendQueue = this.receiveQueue = 0;
        for (NetConnection conn : connectionList) {
            this.sendQueue += conn.getSendQueue();
            this.receiveQueue += conn.getReceiveQueue();
            this.tcpStates[conn.getState()]++;
        }
    }

    public int getByName(String propertyName) {
        try {
            BeanInfo info = Introspector.getBeanInfo(NetworkStats.class);
            PropertyDescriptor[] descriptors = info.getPropertyDescriptors();
            for (PropertyDescriptor descriptor : descriptors) {
                if (descriptor.getName().equals(propertyName)) {
                    Integer value = (Integer) descriptor.getReadMethod().invoke(this);
                    return value;
                }
            }
            throw new RuntimeException("Couldn't find property " + propertyName + " on NetworkStats");
        } catch (Exception e) {
            throw new RuntimeException("Couldn't read property " + propertyName + " from NetworkStats", e);
        }
    }

    public int getTcpInboundTotal() {
        return this.tcpInboundTotal;
    }

    public int getTcpOutboundTotal() {
        return this.tcpOutboundTotal;
    }

    public int getAllInboundTotal() {
        return this.allInboundTotal;
    }

    public int getAllOutboundTotal() {
        return this.allOutboundTotal;
    }

    public int[] getTcpStates() {
        return this.tcpStates;
    }

    //state counters
    public int getTcpEstablished() {
        return this.tcpStates[NetFlags.TCP_ESTABLISHED];
    }

    public int getTcpSynSent() {
        return this.tcpStates[NetFlags.TCP_SYN_SENT];
    }

    public int getTcpSynRecv() {
        return this.tcpStates[NetFlags.TCP_SYN_RECV];
    }

    public int getTcpFinWait1() {
        return this.tcpStates[NetFlags.TCP_FIN_WAIT1];
    }

    public int getTcpFinWait2() {
        return this.tcpStates[NetFlags.TCP_FIN_WAIT2];
    }

    public int getTcpTimeWait() {
        return this.tcpStates[NetFlags.TCP_TIME_WAIT];
    }

    public int getTcpClose() {
        return this.tcpStates[NetFlags.TCP_CLOSE];
    }

    public int getTcpCloseWait() {
        return this.tcpStates[NetFlags.TCP_CLOSE_WAIT];
    }

    public int getTcpLastAck() {
        return this.tcpStates[NetFlags.TCP_LAST_ACK];
    }

    public int getTcpListen() {
        return this.tcpStates[NetFlags.TCP_LISTEN];
    }

    public int getTcpClosing() {
        return this.tcpStates[NetFlags.TCP_CLOSING];
    }

    public int getTcpIdle() {
        return this.tcpStates[NetFlags.TCP_IDLE];
    }

    public int getTcpBound() {
        return this.tcpStates[NetFlags.TCP_BOUND];
    }
}
