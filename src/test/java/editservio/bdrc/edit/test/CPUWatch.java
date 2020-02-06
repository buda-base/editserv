package editservio.bdrc.edit.test;

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

public class CPUWatch implements Runnable {

    public CPUWatch() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void run() {
        OperatingSystemMXBean mx = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        while (true) {
            System.out.println("System Load Average >>" + mx.getSystemLoadAverage());
            System.out.println("System Process CPU time >>" + mx.getProcessCpuTime());
            System.out.println("System CPU load >>" + mx.getSystemCpuLoad());
            try {
                Thread.currentThread().sleep(6000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        new CPUWatch().run();
    }

}
