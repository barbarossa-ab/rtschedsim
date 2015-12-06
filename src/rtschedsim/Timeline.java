package rtschedsim;

import java.util.ArrayList;
import java.util.Iterator;
import rtsched_util.Log;
import rtschedsim.Scheduler.Task;

/**
 *
 * @author barbarossa
 */
public class Timeline {

    private ArrayList <ArrayList <Event>> events;
	private int finishTime;
	private int simTime = 0;
    private int timeUnitMillis;
    private Scheduler scheduler;

    private static Timeline instance;
    
    public Timeline() {
        events = new ArrayList <ArrayList <Event>> ();
    }
    
    public static Timeline getInstance() {
        if(instance == null) {
            instance = new Timeline();
        }
        return instance;
    }
    
    public ArrayList <Event> getCurrentEvents() {
        return events.get(simTime);
    }
    
    public int getSimTime() {
        return simTime;
    }
    
	public void setFinishTime(int finishTime) {
		this.finishTime = finishTime;
        
        int i = 0;
        while(i < finishTime) {
            events.add(new ArrayList <Event> ());
            i++;
        }
	}
    
    public int getFinishTime(){
        return finishTime;
    }
    
    
    public void attachScheduler(Scheduler sch) {
        this.scheduler = sch;
        this.scheduler.setTimeline(this);
    }
    
    
    public void addEvent(Event e) {
        if (e.getTime() < finishTime) {
            events.get(e.getTime()).add(e);
            Log.println("Timeline : added new event " + e);
        }
    }
    
    public void removeEvent (Event e) {
        if (e.getTime() < finishTime) {
            events.get(e.getTime()).remove(e);
            Log.println("Timeline : removed event " + e);
        }
    }

	public void addAllPeriodicEvents(ArrayList <Task> tasks){
		Iterator <Task> it = tasks.iterator();
		while(it.hasNext()){
			addPeriodicEvent(it.next());
		}
	}
	
	public void addPeriodicEvent(Task task) {
		int t = 0;
//		Scenario.printLogFile("TIMELINE : Adding periodic event");
		while(t < finishTime) {
            if (task.getType() == Task.TASK_TYPE_PERIODIC) {
                addEvent(new Event(Event.EVTYPE_PERIOD_START, t, task));
            } else if (task.getType() == Task.TASK_TYPE_APSERVER) {
                addEvent(new Event(Event.EVTYPE_PERIOD_START_APSERVER, t, task));
            }
			t += task.getPeriod();
		}
	}
    
    
    public void run() {
        while (simTime < finishTime) {
            try {
                Thread.sleep(getTimeUnitMillis());	// time unit = 0.5 secunde
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

    		Log.println("--------------------------------------------------------------------------------");
        	Log.println("TIMELINE : Current simulation time = " + simTime);
            scheduler.wakeUp(events.get(simTime));
            
            simTime++;
        }
    }

    

    public void printAllEvents() {
		Iterator <ArrayList<Event>> it = events.iterator();
        int index = 0;
        
		Log.println("TIMELINE : Printing Events ");
		while(it.hasNext()) {
            Log.println("[" + (index++) + "] : " + it.next());
		}
    }

    public int getTimeUnitMillis() {
        return timeUnitMillis;
    }

    public void setTimeUnitMillis(int timeUnitMillis) {
        this.timeUnitMillis = timeUnitMillis;
    }
    
    
    public static class Event {
        public static final int EVTYPE_PERIOD_START               = 1;
        public static final int EVTYPE_APTASK_RELEASE             = 2;
        public static final int EVTYPE_PERIOD_START_APSERVER      = 3;
        public static final int EVTYPE_REPLENISHMENT              = 4;
        public static final int EVTYPE_SLACK_UPDATE               = 5;
        
        private int type;
        private int time;
        private Task task;
        private int replTime;
        private int aiGain;
        
        public Event(int type, int time, Task task) {
            this.type = type;
            this.time = time;
            this.task = task;
        }

        
        public Event(int type, int time, Task task, int specialTime) {
            this.type = type;
            this.time = time;
            this.task = task;
            if(type == EVTYPE_REPLENISHMENT) {
                this.replTime = specialTime;
            }
            if(type == EVTYPE_SLACK_UPDATE) {
                this.aiGain = specialTime;
            }
        }
        
        
        @Override
        public String toString() {
            return getType() + " " + getTime() + ":" + getTask().getName() 
                    + (getType() == EVTYPE_REPLENISHMENT ? (" repl:" + replTime): "")
                    + (getType() == EVTYPE_SLACK_UPDATE ? (" aiGain:" + getAiGain()): "");
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getTime() {
            return time;
        }

        public void setTime(int time) {
            this.time = time;
        }

        public Task getTask() {
            return task;
        }

        public void setTask(Task task) {
            this.task = task;
        }

        public int getReplTime() {
            return replTime;
        }

        public void setReplTime(int replTime) {
            this.replTime = replTime;
        }

        public int getAiGain() {
            return aiGain;
        }

        public void setAiGain(int aiGain) {
            this.aiGain = aiGain;
        }
        
        
    }
    
    
}
