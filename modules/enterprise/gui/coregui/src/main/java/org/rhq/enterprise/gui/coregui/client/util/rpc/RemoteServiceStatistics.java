/*
 * RHQ Management Platform
 * Copyright (C) 2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.util.rpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rhq.enterprise.gui.coregui.client.util.rpc.RemoteServiceStatistics.Record.Summary;

/**
 * @author Joseph Marques
 */
public class RemoteServiceStatistics {

    public static class Record {
        private String serviceName;
        private String methodName;
        private List<Long> data = new ArrayList<Long>();

        private Record(String remoteService, long millis) {
            // remoteService format "{ServiceName}_Proxy.{MethodName}"
            this.serviceName = remoteService.substring(0, remoteService.indexOf("_Proxy"));
            this.methodName = remoteService.substring(remoteService.indexOf('.') + 1);
            this.data.add(millis);
        }

        private void record(long millis) {
            this.data.add(millis);
        }

        public Summary getSummary() {
            return new Summary(serviceName, methodName, data);
        }

        public static class Summary {
            private String serviceName;
            private String methodName;
            private int count;
            private long slowest;
            private long average;
            private long fastest;
            private long stddev;

            private Summary(String serviceName, String methodName, List<Long> data) {
                // remoteService format "{ServiceName}_Proxy.{MethodName}"
                this.serviceName = serviceName;
                this.methodName = methodName;

                this.count = data.size();

                if (!data.isEmpty()) {
                    int i = 0;
                    long total = 0;
                    for (long next : data) {
                        if (i++ == 0) {
                            total = slowest = fastest = next;
                        } else {
                            total += next;
                            if (next > slowest) {
                                slowest = next;
                            } else if (next < fastest) {
                                fastest = next;
                            }
                        }
                    }
                    double avg = (total) / (double) count;

                    double sumOfSquares = 0;
                    for (long next : data) {
                        sumOfSquares += Math.pow(next - avg, 2);
                    }
                    stddev = (long) Math.pow(sumOfSquares / count, 0.5);
                    average = (long) avg;
                }
            }

            public String getServiceName() {
                return serviceName;
            }

            public String getMethodName() {
                return methodName;
            }

            public int getCount() {
                return count;
            }

            public long getSlowest() {
                return slowest;
            }

            public long getAverage() {
                return average;
            }

            public long getFastest() {
                return fastest;
            }

            public long getStddev() {
                return stddev;
            }

            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("serviceName=").append(serviceName).append(',');
                builder.append("methodeName=").append(methodName).append(": ");
                if (count < 1) {
                    builder.append("empty");
                } else {
                    builder.append("count=").append(count).append(',');
                    builder.append("slowest=").append(slowest).append(',');
                    builder.append("average=").append(average).append(',');
                    builder.append("fastest=").append(fastest).append(',');
                    builder.append("stddev=").append(stddev);
                }
                return builder.toString();
            }
        }

    }

    private static Map<String, Record> statistics = new HashMap<String, Record>();

    private RemoteServiceStatistics() {
        // static access only
    }

    public static void record(String remoteService, long millis) {
        Record record = statistics.get(remoteService);
        if (record == null) {
            record = new Record(remoteService, millis);
            statistics.put(remoteService, record);
        } else {
            record.record(millis);
        }
    }

    public static String recordAndPrint(String remoteService, long millis) {
        record(remoteService, millis);
        return print(remoteService);
    }

    public static String print(String remoteService) {
        Record record = statistics.get(remoteService);
        if (record == null) {
            record = new Record(remoteService, 0);
        }
        return "RemoteServiceStatistics: " + remoteService + ": " + record.getSummary();
    }

    public static List<String> printAll() {
        List<String> stats = new ArrayList<String>();

        for (String remoteService : statistics.keySet()) {
            stats.add(print(remoteService));
        }

        return stats;
    }

    public static Summary get(String remoteService) {
        Record stat = statistics.get(remoteService);
        if (stat == null) {
            stat = new Record(remoteService, 0);
        }
        return stat.getSummary();
    }

    public static List<Summary> getAll() {
        List<Summary> stats = new ArrayList<Summary>();

        for (String remoteService : statistics.keySet()) {
            stats.add(get(remoteService));
        }

        return stats;
    }
}
