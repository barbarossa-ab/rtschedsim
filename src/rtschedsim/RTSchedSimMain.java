package rtschedsim;

import rtsched_util.Log;
import rtsched_util.Parser;
import rtsched_util.MathUtil;

/**
 *
 * @author barbarossa
 */
public class RTSchedSimMain {

    public static void main(String[] args) {
        Scheduler sch = Scheduler.getInstance();
        Timeline timeline = Timeline.getInstance();

        timeline.attachScheduler(sch);
        
        
        Log.init("log01", "resp01", true);
        Parser.init("conf01", sch, timeline);
        Parser.readParameters();
        
        sch.rmaPriorityAssignment();
        timeline.printAllEvents();

        timeline.run();
        
//        System.out.println("Poisson with 14 : ");
//        for(int i = 1 ; i <= 10; i++) {
//            System.out.println(MathUtil.getPoisson(14));
//        }
//        
//        System.out.println("Exponential with 10 : ");
//        for(int i = 1 ; i <= 10; i++) {
//            System.out.println(MathUtil.getExpDistributed(1.00/10));
//        }
        
        sch.writeAperiodicSummary();
        
    }
    
    
}




