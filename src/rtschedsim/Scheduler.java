package rtschedsim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import rtsched_util.Log;

/**
 *
 * @author barbarossa
 */
public class Scheduler {
    
    // Constants
    public static final int MAX_PRIORITY    = 127;
    public static final int MIN_PRIORITY    = 0;
    public static final int MAX_PRIORITY_RMA = 63;
    public static final int RMA_STEP = 2;
    
   
    // Common attributes
    private ArrayList <Task> allSystemTasks;
    private ArrayList <Task> periodicTasks;     
    private ArrayList <Task> rmaSchedulableTasks;     
    private ArrayList <Task> aperiodicTasks;
    private Task apServer;
    
    private LinkedList <Task> readyQueue;       // tasks ready to run, ordered by priority
    private LinkedList <Task> idleQueue;        // tasks waiting to be released
    private Task running;                       // currently running task
    private LinkedList <Task> aperiodicQueue;   // aperiodic tasks waiting to be executed
    private int utilFact;                       // utilization factor
    
    private Timeline timeline;
    
    private static Scheduler instance;

    public Scheduler() {
        allSystemTasks = new ArrayList <Task> ();
        periodicTasks = new ArrayList <Task> ();
        
        rmaSchedulableTasks = new ArrayList <Task> () {
            // add ordered by period - ascending order
            @Override
            public boolean add(Task e) {
                Iterator<Task> it;
                int index;
                
                for(it = this.iterator(), index = 0;
                        it.hasNext() && (e.period > it.next().period); index++) {}
                this.add(index, e);
                
                return true;
            }
        };
        
        aperiodicTasks = new ArrayList <Task> ();
        
        readyQueue = new LinkedList <Task> () {
            // add ordered by priority - descending order
            @Override
            public boolean add(Task e) {
                Iterator<Task> it;
                int index;
                
                for(it = this.iterator(), index = 0;
                        it.hasNext() 
                        && (e.priority < it.next().priority); index++) {}
                this.add(index, e);
                
                return true;
            }
        };
        
        idleQueue = new LinkedList <Task> ();
        aperiodicQueue = new LinkedList <Task> ();
    }
    
    public static Scheduler getInstance() {
        if(instance == null) {
            instance = new Scheduler();
        }
        return instance;
    }
    
    // schedule - select the ready task with the highest priority
    public void schedule() {
        if(getRunning() != null) {
            if (!readyQueue.isEmpty()) {
                if (getRunning().getPriority() < getReadyQueue().getFirst().getPriority() 
                        || ((getReadyQueue().getFirst() == apServer)
                            && (getRunning().getPriority() 
                                == getReadyQueue().getFirst().getPriority()))) {
                    // preemption
                    Task newRunning = getReadyQueue().removeFirst();
                    
                    getReadyQueue().add(getRunning());
                    getRunning().setState(Task.TASK_STATE_READY);
                    Log.println("Task [" + getRunning() + "] preempted");

                    setRunning(newRunning);
                    getRunning().setState(Task.TASK_STATE_RUNNING);
                    Log.println("Task [" + getRunning() + "] assigned to CPU");
                }
            }
        } else if(!readyQueue.isEmpty()) {
            setRunning(getReadyQueue().removeFirst());
            getRunning().setState(Task.TASK_STATE_RUNNING);
            Log.println("Task [" + getRunning() + "] assigned to CPU");
        }
    }
    
    
    // wakeUp - this will be executed every time unit passed
    public void wakeUp( ArrayList <Timeline.Event> events ) {

        ((ApServer)apServer).onTimeUnitPassed();
        
        // simulate that running task has executed for a TU 
        executeTimeUnit();
        
        // check deadlines for all tasks
        checkDeadlines();
        
        // treat events
        Iterator <Timeline.Event> it = events.iterator();
        while(it.hasNext()) {
            Timeline.Event e = it.next();
            switch (e.getType()) {
                case Timeline.Event.EVTYPE_PERIOD_START:
                    getIdleQueue().remove(e.getTask());
                    getReadyQueue().add(e.getTask());
                    e.getTask().setRemainingExTime(e.getTask().getCost());
                    e.getTask().setAbsDeadline(timeline.getSimTime() 
                            + e.getTask().getPeriod());
                    e.getTask().setState(Task.TASK_STATE_READY);
                    Log.println("Periodic task [" + e.getTask() + "] released, absDeadline = " 
                            + e.getTask().getAbsDeadline());
                    
                    break;

                case Timeline.Event.EVTYPE_APTASK_RELEASE:
                    Log.println("Aperiodic task [" + e.getTask() + "] released");
//                    e.getTask()
                    ((ApServer)apServer).onApTaskRelease(e.getTask());
                    break;
                    
                case Timeline.Event.EVTYPE_PERIOD_START_APSERVER:
                    Log.println("Aperiodic server [" + apServer + "] period start");
                    ((ApServer)e.getTask()).onPeriodStart();
                    break;
                    
                case Timeline.Event.EVTYPE_REPLENISHMENT :
                    Log.println("Aperiodic server [" + apServer + "] has a replenishment of " 
                            + e.getReplTime());
                    ((SporadicServer)apServer).replenish(e.getReplTime());
                    break;
                    
                case Timeline.Event.EVTYPE_SLACK_UPDATE :
                    Log.println("Aperiodic server [" + apServer + "] has a slack gain of " 
                            + e.getAiGain() + " at the prilevel of " + e.getTask());
                    ((SlackStealingServer)apServer).onAiCounterUpdate(e.getTask(), e.getAiGain());
                    break;
                    
                default :
                    break;
            }
        }
        
        
        ((ApServer)apServer).preSchedule();
        
        // schedule
        schedule();
        
        // aperiodic server routine
        ((ApServer)apServer).postSchedule();
    }
    
    
    public void executeTimeUnit() {
        if (getRunning() != null)   {
            getRunning().executeTimeUnit();
            // TODO : Introduce terminatedCapacity action inside server code
            if (getRunning().finished() || getRunning().terminatedCapacity()) {
                getIdleQueue().add(getRunning());
                getRunning().setState(Task.TASK_STATE_IDLE);
                setRunning(null);
            }
        }
    }
    
    public void checkDeadlines() {
        
        if(getRunning() != null 
                && (getRunning().getAbsDeadline() <= timeline.getSimTime())) {
                Log.println("##ERROR## : Task " + getRunning() + "missed its deadline");
                System.err.println("##ERROR## : Task " + getRunning() + "missed its deadline");
                idleQueue.add(getRunning());
                getRunning().setState(Task.TASK_STATE_IDLE);
                running = null;
        }

        Task t = null;
        Iterator it = getReadyQueue().iterator();
        if(it.hasNext()) {
            t = (Task)it.next();
        }
                
        for (;  it.hasNext(); 
                t = (Task)it.next()) {
            
            if (t.getAbsDeadline() <= timeline.getSimTime()) {
                Log.println("##ERROR## : Task " + t + "missed its deadline");
                System.err.println("##ERROR## : Task " + t + "missed its deadline");
                it.remove();
                idleQueue.add(t);
                t.state = Task.TASK_STATE_IDLE;
            }
        }
    }
    
    public void addTask(Task t) {
        allSystemTasks.add(t);
        getIdleQueue().add(t);       // initially task is idle
        t.state = Task.TASK_STATE_IDLE;
        
        if(t.getType() == Task.TASK_TYPE_PERIODIC) {
            periodicTasks.add(t);
            rmaSchedulableTasks.add(t);
        } else if (t.getType() == Task.TASK_TYPE_APERIODIC) {
            aperiodicTasks.add(t);
        }
    }
    
    
	public Task getTaskByName(String name){
		Iterator <Task> it = allSystemTasks.iterator();
		while(it.hasNext()){
			Task temp = it.next();
			if(temp.getName().equals(name)){
				return temp;
			}
		}
		return null;
	}
    
    public ArrayList <Task> getPeriodicTasks() {
        return periodicTasks;
    }
    
    public void configureBackgroundSched(String name) {
        apServer = new BackgroundServer(name, aperiodicQueue);
        addTask(apServer);
        ((ApServer)apServer).scheduler = this;
    }
    
    public void configurePollingServerSched(String name, int period, int cost) {
        apServer = new PollingServer(name, period, cost, aperiodicQueue);
        addTask(apServer);
        ((ApServer) apServer).scheduler = this;

        rmaSchedulableTasks.add(apServer);
    }
    
    public void configureDefferableServerSched(String name, int period, int cost) {
        apServer = new DefferableServer(name, period, cost, aperiodicQueue);
        addTask(apServer);
        ((ApServer) apServer).scheduler = this;

        rmaSchedulableTasks.add(apServer);
    }
    
    public void configurePriorityExchangeServerSched(String name, int period, int cost) {
    }

    public void configureSporadicServerSched(String name, int period, int capacity) {
        apServer = new SporadicServer(name, period, capacity, aperiodicQueue);
        addTask(apServer);
        ((ApServer)apServer).scheduler = this;
        
        rmaSchedulableTasks.add(apServer);
    }

    public void configureSlackStealingServerSched(String name) {
        apServer = new SlackStealingServer(name, rmaSchedulableTasks, aperiodicQueue);
        addTask(apServer);
        apServer.setPriority(MAX_PRIORITY);
        ((ApServer)apServer).scheduler = this;
    }


    
    public void rmaPriorityAssignment() {
        ArrayList <Task> tasksToOrder = rmaSchedulableTasks;
        
        //	the shorter the period the higher the priority
        Iterator<Task> it = tasksToOrder.iterator();
        int prevPeriod = tasksToOrder.get(0).period;
        int currPeriod;
        int prevPriority = MAX_PRIORITY_RMA;

        Log.println("SCHEDULER : RMA Priority Assignment");
        Log.println("RMA Schedulable tasks " + rmaSchedulableTasks);

        while (it.hasNext()) {
            Task temp = it.next();
            currPeriod = temp.period;
            
            if (currPeriod == prevPeriod) {
                temp.priority = prevPriority;
            } else {
                temp.priority = prevPriority - RMA_STEP;
            }
            prevPeriod = currPeriod;
            prevPriority = temp.priority;
            
            Log.println("SCHEDULER : RMA : Task " + temp.getName()
                    + " assigned priority " + temp.priority);
        }
    }

    public LinkedList <Task> getReadyQueue() {
        return readyQueue;
    }

    public void setReadyQueue(LinkedList <Task> readyQueue) {
        this.readyQueue = readyQueue;
    }

    public LinkedList <Task> getIdleQueue() {
        return idleQueue;
    }

    public void setIdleQueue(LinkedList <Task> idleQueue) {
        this.idleQueue = idleQueue;
    }

    public Task getRunning() {
        return running;
    }

    public void setRunning(Task running) {
        this.running = running;
    }

    public boolean hasPeriodicApServer() {
        if ((((ApServer) apServer).getSType() == ApServer.SCHED_TYPE_POLLING)
                || (((ApServer) apServer).getSType() == ApServer.SCHED_TYPE_DEFFERABLE)
                ) {
            return true;
        }
        return false;
    }

    public Task getApServer() {
        return apServer;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    
    public void writeAperiodicSummary() {
        Log.resPrintln("Aperiodic Activity Summary");
        Log.resPrintln("Server : " + apServer);
        if(hasPeriodicApServer() 
                || ((ApServer)apServer).getSType() == ApServer.SCHED_TYPE_SPORADIC) {

            double sysUtilisation = 0;
            int nrTasks = 0;
            
            for(Task t : periodicTasks) {
               sysUtilisation += ((double)t.getCost() / (double)t.getPeriod());
               nrTasks++;
            }
            if(((ApServer)apServer).getSType() == ApServer.SCHED_TYPE_SPORADIC) {
                sysUtilisation += ((double)((SporadicServer)apServer).capacity 
                        / ((SporadicServer)apServer).period);
                nrTasks++;
            }
            
            Log.resPrintln("Nr periodic tasks (apServer included if periodic) : " + nrTasks);
            Log.resPrintln("System utilisation : " + sysUtilisation);
            Log.resPrintln("Ulub : " + nrTasks * (Math.pow(2.00, 1.00 / nrTasks) - 1)
                    );
            
        }
        
        Log.resPrintln("Total nr of aperiodic tasks : " + aperiodicTasks.size());
        Log.resPrintln("------------------------------------------------------");
        Log.resPrintln("Finish time - Release time = Responsiveness");
        
        float avgResponseTime = 0;
        int n = 0;
        
        for(Task t : aperiodicTasks) {
            Log.resPrintln(t.getFinishTime() 
                    + " - " + t.getReleaseTime()
                    + " = " + (t.getFinishTime() - t.getReleaseTime()));
            if(t.getFinishTime() != 0) {
                n++;
                avgResponseTime += (t.getFinishTime() - t.getReleaseTime());
            }
        }
        
        avgResponseTime /= n; 
        Log.resPrintln("Nr of finished tasks : " + n);
        Log.resPrintln("Average response time : " + avgResponseTime);
    }
    
    
    public static class Task {

        public static final int TASK_STATE_IDLE = 0;
        public static final int TASK_STATE_READY = 1;
        public static final int TASK_STATE_RUNNING = 2;
        
        public static final int TASK_TYPE_PERIODIC = 1;
        public static final int TASK_TYPE_APERIODIC = 2;
        public static final int TASK_TYPE_APSERVER = 3;

        protected String name;
        protected int priority;
        protected int state;
        protected int type;
        protected int cost;
        protected int remainingExTime;
        
        private int absDeadline;
        private int releaseTime;
        private int finishTime;
        
        protected Scheduler scheduler;
        
        // Periodic specific attributes
        protected int period;

        // Aperiodic specific attributes
        protected int relDeadline;     // relative deadline
        
        // Aperiodic server specific attributes
        protected Task apTask;

        
        public Task() {
        }
        
        @Override
        public String toString() {
            return ("Name:" + name +
                    " Type:" + (type == TASK_TYPE_PERIODIC ? "P" : "A") + 
                    " Pr:" + priority +
                    " State:" + state +
                    " Cost:" + cost +
                    (type == TASK_TYPE_PERIODIC ? (" P:" + period) : "") +
                    (type == TASK_TYPE_APERIODIC ? (" Rd:" + relDeadline) : "") +
                    " RemExTime:" + remainingExTime);
        }


        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getCost() {
            return cost;
        }

        public void setCost(int cost) {
            this.cost = cost;
        }

        public int getRemainingExTime() {
            return remainingExTime;
        }

        public void setRemainingExTime(int remainingExTime) {
            this.remainingExTime = remainingExTime;
        }

        public int getPeriod() {
            return period;
        }

        public void setPeriod(int period) {
            this.period = period;
        }

        public int getRelDeadline() {
            return relDeadline;
        }

        public void setRelDeadline(int relDeadline) {
            this.relDeadline = relDeadline;
        }

        public void executeTimeUnit() {
            this.remainingExTime--;
            Log.println("Task [" + this + "] executed for a time unit");
        }

        public boolean finished() {
            if(this.remainingExTime == 0) {
                Log.println("Task [" + this + "] finished execution");
                return true;
            } else {
                return false;
            }
        }

        public boolean terminatedCapacity() {
            return false;
        }

        public int getAbsDeadline() {
            return absDeadline;
        }

        public void setAbsDeadline(int absDeadline) {
            this.absDeadline = absDeadline;
        }
        
        public boolean missedDeadline() {
            if(scheduler.getTimeline().getSimTime() >= absDeadline) {
                return true;
            }
            return false;
        }

        /**
         * @return the releaseTime
         */
        public int getReleaseTime() {
            return releaseTime;
        }

        /**
         * @param releaseTime the releaseTime to set
         */
        public void setReleaseTime(int releaseTime) {
            this.releaseTime = releaseTime;
        }

        /**
         * @return the finishTime
         */
        public int getFinishTime() {
            return finishTime;
        }

        /**
         * @param finishTime the finishTime to set
         */
        public void setFinishTime(int finishTime) {
            this.finishTime = finishTime;
        }
    }
    
    
    public static class ApServer extends Task {

        // ApServer types
        public static final int SCHED_TYPE_BACKGROUND       = 1; // bs
        public static final int SCHED_TYPE_POLLING          = 2; // ps
        public static final int SCHED_TYPE_DEFFERABLE       = 3; // ds
        public static final int SCHED_TYPE_PRI_INVERSION    = 4; // pis
        public static final int SCHED_TYPE_SPORADIC         = 5; // sps
        public static final int SCHED_TYPE_SLACK_STEALING   = 6; // sts

        protected int sType;
        protected LinkedList <Task> aperiodicQueue; // aperiodic tasks waiting to be serviced

        // For aperiodic task servers that have periods
        public void onPeriodStart() {
        }
        
        public ApServer() {
            this.type = TASK_TYPE_APSERVER;
        }
        
        @Override
        public void executeTimeUnit() {
            Log.println(this + " executed for a time unit");
            if(!aperiodicQueue.isEmpty()) {
                aperiodicQueue.getFirst().executeTimeUnit();
                if (aperiodicQueue.getFirst().finished()) {
                    aperiodicQueue.getFirst().setFinishTime(
                            scheduler.getTimeline().getSimTime());
                    aperiodicQueue.removeFirst();
                }
            }
        }

        @Override
        public boolean finished() {
            if(aperiodicQueue.isEmpty()) {
                Log.println(this + " finished the aperiodic queue");
                return true;
            } else {
                return false;
            }
        }
        
        public int getSType(){
            return sType;
        }

        public void onApTaskRelease(Task t) {
            aperiodicQueue.addLast(t);
            t.setRemainingExTime(t.getCost());
            t.setReleaseTime(scheduler.getTimeline().getSimTime());
        }

        public void postSchedule() {
        }

        public void preSchedule() {
        }
        
        public void onTimeUnitPassed(){
        }
        
        @Override
        public int getAbsDeadline() {
            return Integer.MAX_VALUE;
        }
        
    }
    
    
    public class BackgroundServer extends ApServer {

        public BackgroundServer(String name, LinkedList <Task> aperiodicQueue ) {
            super();
            this.sType = SCHED_TYPE_BACKGROUND;
            this.priority = MIN_PRIORITY;
            this.aperiodicQueue = aperiodicQueue;
            setName(name);
        }

        @Override
        public void onApTaskRelease(Task t) {
            super.onApTaskRelease(t);
            if (state == Task.TASK_STATE_IDLE) {
                scheduler.getIdleQueue().remove(this);
                scheduler.getReadyQueue().add(this);
                this.setState(Task.TASK_STATE_READY);
            }
        }

        
        @Override
        public String toString() {
            return ("BackgroundServer");
        }

    }

    
    public class PollingServer extends ApServer {

        protected int capacity;
        protected int remCapacity;

        public PollingServer(String name, int psPeriod, int psCapacity, 
                LinkedList <Task> aperiodicQueue ) {
            super();
            this.sType = SCHED_TYPE_POLLING;
            this.aperiodicQueue = aperiodicQueue;
            this.name = name;
            this.period = psPeriod;
            this.capacity = psCapacity;
        }
        
        @Override
        public void executeTimeUnit() {
            super.executeTimeUnit();
            remCapacity--;
        }

//        @Override
//        public void onApTaskRelease(Task t) {
//            super.onApTaskRelease(t);
//            t.setRemainingExTime(t.getCost());
//        }
        
        
        @Override
        public boolean terminatedCapacity() {
            if(remCapacity == 0) {            
                Log.println(this + " terminated its capacity");
                return true;
            } else {
                return false;
            }
        }
        
        @Override
        public void onPeriodStart() {
            if(!aperiodicQueue.isEmpty()) {
                remCapacity = capacity;
                if (state == TASK_STATE_IDLE) {
                    scheduler.getIdleQueue().remove(this);
                    scheduler.getReadyQueue().add(this);
                    state = TASK_STATE_READY;
                }
            } else {
                remCapacity = 0;
            }
        }

        @Override
        public String toString() {
            return ("PollingServer " +
                    "Name:" + name +
                    " Pr:" + priority +
                    " State:" + state +
                    " Period:" + period +
                    " Capacity:" + capacity +
                    " RemCapacity:" + remCapacity);
        }

    }
    
    
    public class DefferableServer extends PollingServer {
  
        public DefferableServer(String name, int psPeriod, int psCapacity, 
                LinkedList <Task> aperiodicQueue ) {
            super(name, psPeriod, psCapacity, aperiodicQueue);
            this.sType = SCHED_TYPE_DEFFERABLE;
        }
        
        @Override
        public void onApTaskRelease(Task t) {
            super.onApTaskRelease(t);
//            t.setRemainingExTime(t.getCost());
            if ((state == Task.TASK_STATE_IDLE) 
                    && !terminatedCapacity()) {
                scheduler.getIdleQueue().remove(this);
                scheduler.getReadyQueue().add(this);
                this.setState(Task.TASK_STATE_READY);
            }
        }
        
        @Override
        public void onPeriodStart() {
            remCapacity = capacity;
            if(!aperiodicQueue.isEmpty()) {
                if (state == TASK_STATE_IDLE) {
                    scheduler.getIdleQueue().remove(this);
                    scheduler.getReadyQueue().add(this);
                    state = TASK_STATE_READY;
                }
            }
        }
        

        @Override
        public String toString() {
            return ("DefferableServer " +
                    "Name:" + name +
                    " Pr:" + priority +
                    " State:" + state +
                    " Period:" + period +
                    " Capacity:" + capacity +
                    " RemCapacity:" + remCapacity);
        }
        
    }
    
    public class PriorityExchangeServer extends ApServer {
        int capacity;
        ArrayList <Integer> priLevelsCapacity;  // cap acumulated at each priority level
        
        public PriorityExchangeServer(String name, int priLevels, int pePeriod, 
                int peCapacity, LinkedList <Task> aperiodicQueue) {
            
            super();
            this.sType = SCHED_TYPE_PRI_INVERSION;
            this.aperiodicQueue = aperiodicQueue;
            this.name = name;
            this.period = pePeriod;
            priLevelsCapacity = new ArrayList <> (priLevels);
        }
        
        
        @Override
        public String toString() {
            return ("PriExchangeServer " +
                    "Name:" + name +
                    " Pr:" + priority +
                    " State:" + state +
                    " Period:" + period +
                    " Capacity:" + capacity +
                    " PriLevelsCapacity:" + priLevelsCapacity);
        }
        
    }
    
    public class SporadicServer extends ApServer {
        
        public static final int SPS_STATE_IDLE      = 0;
        public static final int SPS_STATE_ACTIVE    = 1;
        
        protected int capacity;
        protected int remCapacity;
        
        int prevSpsState ;
        int spsState;   // active or idle
        Timeline.Event currReplEvent;
        int replAmmount;

        public SporadicServer(String name, int spsPeriod, int spsCapacity, 
                LinkedList <Task> aperiodicQueue) {
            super();
            this.sType = SCHED_TYPE_SPORADIC;
            this.name = name;
            this.period = spsPeriod;
            this.remCapacity = this.capacity = spsCapacity;
            this.aperiodicQueue = aperiodicQueue;
            this.prevSpsState = SPS_STATE_IDLE;
            this.replAmmount = 0;
        }
        
        
        @Override
        public void executeTimeUnit() {
            super.executeTimeUnit();
            remCapacity--;
            replAmmount++;
        }

        @Override
        public void onApTaskRelease( Task t) {
            super.onApTaskRelease(t);
//            t.setRemainingExTime(t.getCost());
            if ((state == Task.TASK_STATE_IDLE) 
                    && !terminatedCapacity()) {
                scheduler.getIdleQueue().remove(this);
                scheduler.getReadyQueue().add(this);
                this.setState(Task.TASK_STATE_READY);
            }
        }

        @Override
        public void preSchedule() {
            if (terminatedCapacity()) {
                // capacity consumed
                // establish replenishment ammount
                setReplenishAmmount();
            }
        }
        
        @Override
        public void postSchedule() {
            if((scheduler.getRunning() != null)
                    && (scheduler.getRunning().priority >= priority)) {
                spsState = SPS_STATE_ACTIVE;
                Log.println(this + " is active");
            } else {
                spsState = SPS_STATE_IDLE;
                Log.println(this + " is idle");
            }
            
            if(spsState != prevSpsState) {
                if(spsState == SPS_STATE_ACTIVE) {
                    // idle - active transition
                    Log.println(this + " has idle to active transition");
                    
                    if(remCapacity > 0) {
                        replAmmount = 0;
                        
                        // plan a replenishment
                        timeline.addEvent( currReplEvent =
                                new Timeline.Event(Timeline.Event.EVTYPE_REPLENISHMENT, 
                                    timeline.getSimTime() + period, 
                                    this,
                                    0));
                        Log.println(this + " planned a replenishment for time " + 
                                (timeline.getSimTime() + period));
                    }
                } else {
                    // active - idle transition
                    Log.println(this + " has active to idle transition");

                    // establish replenishment ammount
                    if(replAmmount != 0) {
                        setReplenishAmmount();
                    } 
//                    else if(currReplEvent.getReplTime() != 0){
//                        timeline.removeEvent(currReplEvent);
//                    }
                }
            }
            
            prevSpsState = spsState;
        }
        
        public void setReplenishAmmount() {
            if ((currReplEvent != null) && (replAmmount != 0)) {
                Log.println(this + " sets a replenish ammount of " 
                        + replAmmount + " at time " + currReplEvent.getTime());
                currReplEvent.setReplTime(replAmmount);
                replAmmount = 0;
            }
        }

        
        @Override
        public boolean terminatedCapacity() {
            if (remCapacity == 0) {
                Log.println(this + " terminated its capacity");
                return true;
            } else {
                return false;
            }
        }
        
        
        public void replenish(int ammount) {
            remCapacity += ammount;
            if(remCapacity > capacity) {
                remCapacity = capacity;
            }
            if ((state == Task.TASK_STATE_IDLE) 
                    && !terminatedCapacity()
                    && !aperiodicQueue.isEmpty()) {
                scheduler.getIdleQueue().remove(this);
                scheduler.getReadyQueue().add(this);
                this.setState(Task.TASK_STATE_READY);
            }
        }

        
        @Override
        public String toString() {
            return ("SporadicServer " +
                    "Name:" + name +
                    " Pr:" + priority +
                    " State:" + state +
                    " Period:" + period +
                    " Capacity:" + capacity +
                    " RemCapacity:" + remCapacity +
                    " A/I-State:" + spsState);
        }
        
    }

    
    public class SlackStealingServer extends ApServer {
        
        // sorted by priority RMA-style
        private ArrayList <Task> periodicTasks;
        private int aiCounters[];
        private int aperiodicActivity;
        private int slack;
        

        public SlackStealingServer(String name, 
                ArrayList <Task> periodicTasks, 
                LinkedList <Task> aperiodicQueue) {
            
            super();
            this.sType = SCHED_TYPE_SLACK_STEALING;
            this.name = name;
            this.periodicTasks = periodicTasks;
            this.aperiodicQueue = aperiodicQueue;
            
            this.aiCounters = new int[periodicTasks.size()];
        }
        

        @Override
        public void executeTimeUnit() {
            super.executeTimeUnit();
            slack--;
            aperiodicActivity++;
            Log.println("Aperiodic activity incremented, new value = " + aperiodicActivity);
        }

        
        @Override
        public void onTimeUnitPassed() {
            if(scheduler.getTimeline().getSimTime() != 0) {
                updateInactivity();
            }
        }
        
        @Override
        public void preSchedule() {
            updateSlack();
            if(!terminatedSlack()) {
                if((this.state == TASK_STATE_IDLE) 
                        && !aperiodicQueue.isEmpty()) {
                    scheduler.getIdleQueue().remove(this);
                    scheduler.getReadyQueue().add(this);
                    state = TASK_STATE_READY;
                }
            }
        }

        @Override
        public void onApTaskRelease(Task t) {
            super.onApTaskRelease(t);
            t.setRemainingExTime(t.getCost());
            if ((state == Task.TASK_STATE_IDLE)
                    && !terminatedCapacity()) {
                scheduler.getIdleQueue().remove(this);
                scheduler.getReadyQueue().add(this);
                this.setState(Task.TASK_STATE_READY);
            }
        }


        public void updateInactivity() {
            // decrement aiCounters for inactivity
            if (state != TASK_STATE_RUNNING) {
                for (int i = 1; i <= periodicTasks.size()
                        && !taskAtPriLevel(i).equals(scheduler.getRunning()); i++) {
                    aiCounters[i - 1]--;
                    Log.println("AiCounter for priLevel " + i + " decremented, new value = "
                            + aiCounters[i - 1]);
                }
            }
        }


        @Override
        public String toString() {
            return ("SlackStealingServer "
                    + "Name:" + name
                    + " Pr:" + priority
                    + " State:" + state
                    + " Period:" + period
                    + " Slack:" + slack);
        }

        
        public void updateSlack() {
            // get minimum from aiCounters
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < periodicTasks.size(); i++) {
                if (min > aiCounters[i]) {
                    min = aiCounters[i];
                }
            }
            Log.println("Min value for aiCounters = " + min);
            
            slack = min - aperiodicActivity;
            Log.println("New slack value = " + slack);
        }
        
        
        @Override
        public void postSchedule() {
        }
        
        @Override
        public boolean terminatedCapacity() {
            return terminatedSlack();
        }
        
        

        public void onAiCounterUpdate(Task t, int aiGain){
            for (int i = 1 ; i <= periodicTasks.size(); i++) {
                if(taskAtPriLevel(i).equals(t)) {
                    aiCounters[i-1] += aiGain;
                }
            }
        }

        
        public void planAIUpdates() {
            Log.println("Planning slack updates...");
            
            for (int i = 1 ; i <= periodicTasks.size(); i++) {
                Task task = taskAtPriLevel(i);
                
                System.out.println("  For task " + task);
                int releaseNr = 0;
                int inac = 0;
                int newInac;
                int time = 0;
                
                while( (newInac = inactivity (i, releaseNr, inac)) != -1 ) {
                    scheduler.timeline.addEvent(new Timeline.Event(Timeline.Event.EVTYPE_SLACK_UPDATE, 
                            time,
                            task,
                            (newInac - inac - task.getCost()
                            )));
                    
                    time = latestCompletionTime(i, releaseNr, newInac);
                    inac = newInac;
                    releaseNr++;
                }
            }
        }
        
        
        // This function measures the inactivity at the given priority level
        // The analyzed time interval is between the task at the priLevel (1-based)
        // begin of period with releaseNr and end.
        // The accumulated inactivity at the beginning of period is given.
        private int inactivity (int priLevel, int releaseNr, int inactivityAcc) {
            int inactivityAccFin = inactivityAcc;
            int r = releaseNr * periodicTasks.get(priLevel - 1).period;
            int d = (releaseNr + 1) * periodicTasks.get(priLevel - 1).period;
            int x = r;
            int y = d;
            
            if(d >= timeline.getFinishTime()) {
                return -1;
            }
            
            boolean inBusyPeriod = (work(priLevel - 1, x, inactivityAcc) > x);
            
            while(x < d) {
                if(inBusyPeriod) {
                    int dif = y - x;
                    while((dif > 0) && (x < d)) {
                        y = work(priLevel - 1, x, inactivityAccFin);
                        dif = y - x;
                        x = y;
                    }
                    inBusyPeriod = false;
                } else {
                    int i = timeBeforeBusy(priLevel - 1, x);
                    inactivityAccFin += ((i < (d - x)) ? i : (d - x));
                    x += ((i < (d - x)) ? i : (d - x));
                    y = d;
                    inBusyPeriod = true;
                }
            }
            
            return inactivityAccFin;
        }

        
        public int latestCompletionTime(int priLevel, int releaseNr, int inactivityAtFin) {
            int x = releaseNr * periodicTasks.get(priLevel - 1).period;
            int y = work(priLevel - 1, x, inactivityAtFin);
            int z = y - x; 
            
            while(z > 0) {
                y = u(priLevel - 1, x, inactivityAtFin);
                Log.println("   x = " +  x + ", y = " + y);
                z = y - x;
                x = y;
            }

            return x;
        }
        
        
        // priLevel is 1-based
        private int work(int priLevel, int t, int inactivity) {
            int w = inactivity;
            
            for (int j = 1; j <= priLevel; j++) {
                w += h(t, j) * taskAtPriLevel(j).cost ;
            }
            
            return w;
        }
        
        // priLevel is 1-based
        private Task taskAtPriLevel(int priLevel) {
            return periodicTasks.get(priLevel - 1);
        }
        
        private int timeBeforeBusy(int priLevel, int t) {
            int min = Integer.MAX_VALUE;
            
            for(int j = 1 ; j <= priLevel; j++) {
                if(min > (taskAtPriLevel(j).period * h(t,j) - t)) {
                    min = taskAtPriLevel(j).period * h(t,j) - t;
                }
            }
            return min;
        }
        
        private int h(int t, int priLevel) {
            return (int) ((Math.floor((double)t / taskAtPriLevel(priLevel).period) + 1) > 0 ? 
                         (Math.floor((double)t / taskAtPriLevel(priLevel).period) + 1) : 0);
        }
        
        private int H(int t, int priLevel) {
            return (int) ((Math.ceil((double)t / taskAtPriLevel(priLevel).period)) > 0 ? 
                         (Math.ceil((double)t / taskAtPriLevel(priLevel).period)) : 0);
        }
        
        private int u(int priLevel, int t, int inactivity) {
            int u = inactivity;

            for (int j = 1; j <= priLevel; j++) {
                u += H(t, j) * taskAtPriLevel(j).cost ;
            }
            
            return u;
        }
        
        public boolean terminatedSlack() {
            if (slack <= 0) {
                Log.println(this + " exhausted slack, s = " + slack);
                return true;
            } else {
                return false;
            }
        }
        
    }

}

