package rtsched_util;

import java.io.BufferedReader;
import java.io.FileReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import rtschedsim.Scheduler;
import rtschedsim.Timeline;


import rtschedsim.Timeline;

public class Parser {

    // modes used in operation
    public static final int PERIODIC_ONLY_MODE = 0;
    public static final int APERIODIC_ONLY_MODE = 1;
    public static final int FULL_MODE = 2;
    public static final int operationMode = FULL_MODE;
    //	zones used in parsing
    public static final int NOWHERE_ZONE = -1;
    public static final int TASK_ZONE = 0;
    public static final int TASK_PERIODIC_ZONE = 1;
    public static final int TASK_APERIODIC_ZONE = 2;
    public static final int SERVER_ZONE = 3;
    public static final int TIME_ZONE = 4;
    public static final int TIME_EVENTS_ZONE = 5;
    
    private static String inputFileName;
    private static Scheduler scheduler;
    private static Timeline timeline;
    
    private static boolean autoApSpec = false;
    private static int autoApAvgCost;
    private static int autoApAvgInterarrivalTime;

    public static void init(String inputFileName,
            Scheduler scheduler,
            Timeline timeline) {
        Parser.inputFileName = inputFileName;
        Parser.scheduler = scheduler;
        Parser.timeline = timeline;
    }

    public static void readParameters() {
        int zone = NOWHERE_ZONE;
        try {
            System.out.println("PARSER : Reading scenario from file ");
            BufferedReader in = new BufferedReader(new FileReader(inputFileName));
            String temp;
            while ((temp = in.readLine()) != null) {
                StringTokenizer tk = new StringTokenizer(temp, " \t:");

                if (!tk.hasMoreElements()) {
                    continue;		//if line empty, skip
                } else {
                    String firstWord = tk.nextToken();
                    if (firstWord.startsWith("//")) {
                        continue; 		// if line commented, skip
                    }

                    //	Tasks
                    if (firstWord.startsWith("#TASKS")) {
                        System.out.println("PARSER : Entering TASK_ZONE");
                        zone = TASK_ZONE;
                        continue;
                    }
                    if ((zone == TASK_ZONE)
                            && (firstWord.startsWith("#END_TASKS"))) {
                        System.out.println("PARSER : Leaving TASK_ZONE");
                        zone = NOWHERE_ZONE;
                        continue;
                    }

                    //	Periodic tasks
                    if ((zone == TASK_ZONE)
                            && (firstWord.startsWith("#PERIODIC"))) {
                        System.out.println("PARSER : Entering TASK_PERIODIC_ZONE");
                        zone = TASK_PERIODIC_ZONE;
                        continue;
                    }
                    if ((zone == TASK_PERIODIC_ZONE)
                            && (firstWord.startsWith("#END_PERIODIC"))) {
                        zone = TASK_ZONE;
                        continue;
                    }

                    //	Sporadic tasks
                    if ((zone == TASK_ZONE)
                            && (firstWord.startsWith("#APERIODIC"))) {
                        System.out.println("PARSER : Entering TASK_APERIODIC_ZONE");
                        zone = TASK_APERIODIC_ZONE;
                        continue;
                    }
                    if ((zone == TASK_APERIODIC_ZONE)
                            && (firstWord.startsWith("#END_APERIODIC"))) {
                        zone = TASK_ZONE;
                        continue;
                    }

                    //	Servers zone
                    if (firstWord.startsWith("#SERVERS")) {
                        System.out.println("PARSER : Entering SERVER_ZONE");
                        zone = SERVER_ZONE;
                        continue;
                    }
                    if (firstWord.startsWith("#END_SERVERS")) {
                        System.out.println("PARSER : Leaving SERVER_ZONE");
                        zone = NOWHERE_ZONE;
                        continue;
                    }

                    //	Timeline scenario
                    if (firstWord.startsWith("#TIME")) {
                        System.out.println("Entering TIME_ZONE");
                        zone = TIME_ZONE;
                        continue;
                    }
                    if (firstWord.startsWith("#END_TIMELINE")) {
                        System.out.println("Leaving TIME_ZONE");
                        zone = NOWHERE_ZONE;
                        continue;
                    }

                    // Events
                    if ((zone == TIME_ZONE)
                            && firstWord.startsWith("#EVENTS")) {
                        System.out.println("Entering TIME_EVENTS_ZONE");
                        zone = TIME_EVENTS_ZONE;
                        continue;
                    }
                    if ((zone == TIME_EVENTS_ZONE)
                            && firstWord.startsWith("#END_EVENTS")) {
                        System.out.println("Leaving TIME_EVENTS_ZONE");
                        zone = TIME_ZONE;
                        continue;
                    }

                }

                StringTokenizer tk2 = new StringTokenizer(temp, " \t:");

                //	ordinary, info-filled lines, are analyzed here
                switch (zone) {
                    case NOWHERE_ZONE:
                        break;
                    case TASK_ZONE:{
                        String text = (String) tk2.nextToken();
                        if(text.equals("$APERIODIC_SPEC")) {
                            if(tk2.nextToken().equals("AUTO")) {
                                autoApSpec = true;
                                System.out.println("Aperiodic tasks and arrivals auto-generated");
                            }
                        }
                        break;
                    }
                    case TASK_PERIODIC_ZONE:	// period task add
//					if (operationMode == APERIODIC_ONLY_MODE){
//						break;
//					}
//                  TASK_NAME   PERIOD		DURATION

                        String name = (String) tk2.nextToken();
                        int period = Integer.parseInt(tk2.nextToken());
                        int cost = Integer.parseInt(tk2.nextToken());
                        Scheduler.Task dummie = new Scheduler.Task();
                        dummie.setName(name);
                        dummie.setType(Scheduler.Task.TASK_TYPE_PERIODIC);
                        dummie.setPeriod(period);
                        dummie.setCost(cost);

                        System.out.println(dummie);
                        scheduler.addTask(dummie);
                        break;

                    case TASK_APERIODIC_ZONE:	// aperiodic task add 
//					if (operationMode == PERIODIC_ONLY_MODE){
//						break;
//					}
//                  TASK_NAME   COST    SOFT_REL_DEADLINE
                    {
                        if (!autoApSpec) {
                            String name2 = (String) tk2.nextToken();
                            int cost2 = Integer.parseInt(tk2.nextToken());
                            int softRelDel = Integer.parseInt(tk2.nextToken());
                            Scheduler.Task dummie2 = new Scheduler.Task();
                            dummie2.setName(name2);
                            dummie2.setType(Scheduler.Task.TASK_TYPE_APERIODIC);
                            dummie2.setCost(cost2);
                            dummie2.setRelDeadline(softRelDel);

                            System.out.println(dummie2);
                            scheduler.addTask(dummie2);

                        } else {
                            String avgText = (String) tk2.nextToken();
                            if(avgText.equals("$AVG_COST")){
                                autoApAvgCost = Integer.parseInt(tk2.nextToken());
                                System.out.println("Aperiodic average cost set to " + autoApAvgCost);
                            }
                        }
                        break;

                    }
                        
                    case SERVER_ZONE:	// sporadic task add
//					Parsing a task information
//					if (operationMode == PERIODIC_ONLY_MODE){
//						break;
//					}
//SERVER-ID		TYPE		    OTHER_PARAMS
//1             BACKGROUND
//SERVER-ID		TYPE		    PERIOD		CAP
//S2			POLLING		    5			1
//SERVER-ID		TYPE		    PERIOD		CAP
//3				DEFFERABLE		5			1
                        String name3 = (String) tk2.nextToken();
                        String type = (String) tk2.nextToken();
                        switch (type) {
                            case "BACKGROUND":
                                scheduler.configureBackgroundSched(name3);
                                break;
                            case "POLLING":
                                int psPeriod = Integer.parseInt(tk2.nextToken());
                                int psCapacity = Integer.parseInt(tk2.nextToken());
                                scheduler.configurePollingServerSched(name3, psPeriod, psCapacity);
                                break;
                            case "DEFFERABLE": {
                                int dsPeriod = Integer.parseInt(tk2.nextToken());
                                int dsCapacity = Integer.parseInt(tk2.nextToken());
                                scheduler.configureDefferableServerSched(name3, dsPeriod, dsCapacity);
                                break;
                            }
                            case "PRI_EXCHANGE": {
                                int dsPeriod = Integer.parseInt(tk2.nextToken());
                                int dsCapacity = Integer.parseInt(tk2.nextToken());
                                scheduler.configurePriorityExchangeServerSched(name3, dsPeriod, dsCapacity);
                                break;
                            }
                            case "SPORADIC":
                                int spsPeriod = Integer.parseInt(tk2.nextToken());
                                int spsCapacity = Integer.parseInt(tk2.nextToken());
                                scheduler.configureSporadicServerSched(name3, spsPeriod, spsCapacity);
                                break;
                            case "SLACK_STEALING":
                                scheduler.configureSlackStealingServerSched(name3);
                                break;
                        }
                        break;

                    case TIME_ZONE: {
                        String s = tk2.nextToken();
                        if (s.equals("$TIME_UNIT")) {
                            int tu = Integer.parseInt(tk2.nextToken());
                            timeline.setTimeUnitMillis(tu);
                        }
                        if (s.equals("$FINISH_TIME")) {
                            int time = Integer.parseInt(tk2.nextToken());
                            timeline.setFinishTime(time);
                        }
                        break;
                    }

                    case TIME_EVENTS_ZONE: {
//					if (operationMode == PERIODIC_ONLY_MODE){
//						break;
//					}
//	TIME : 	TASK_NAME
                        if (!autoApSpec) {
                            int time = Integer.parseInt(tk2.nextToken());
                            String taskName = tk2.nextToken();

                            Timeline.Event e = new Timeline.Event(
                                    Timeline.Event.EVTYPE_APTASK_RELEASE,
                                    time,
                                    scheduler.getTaskByName(taskName));
                            timeline.addEvent(e);
                        } else {
                            String avgText = (String) tk2.nextToken();
                            if (avgText.equals("$AVG_INTERARRIVAL_TIME")) {
                                autoApAvgInterarrivalTime = Integer.parseInt(tk2.nextToken());
                                System.out.println("Aperiodic average interarrival time set to " + autoApAvgInterarrivalTime);
                            }
                        }
                        break;

                    }
                }
            }

            if (operationMode != APERIODIC_ONLY_MODE) {
                ArrayList<Scheduler.Task> periodicTasks =
                        new ArrayList<>(scheduler.getPeriodicTasks());
                if (scheduler.hasPeriodicApServer()) {
                    Log.println("Scheduler has periodic ap server");
                    periodicTasks.add(scheduler.getApServer());
                }
                timeline.addAllPeriodicEvents(periodicTasks);

                if (((Scheduler.ApServer) scheduler.getApServer()).getSType()
                        == Scheduler.ApServer.SCHED_TYPE_SLACK_STEALING) {
                    ((Scheduler.SlackStealingServer) scheduler.getApServer()).planAIUpdates();
                }
            }
            
            if(autoApSpec) {
                int time = 0, taskNr = 1;
                
                while (time < timeline.getFinishTime()) {
                    Scheduler.Task dummie = new Scheduler.Task();
                    dummie.setName("J" + taskNr++);
                    dummie.setType(Scheduler.Task.TASK_TYPE_APERIODIC);
                    int cost = MathUtil.getPoisson(autoApAvgCost);
                    while(cost == 0) {
                        cost = MathUtil.getPoisson(autoApAvgCost);
                    }
                    dummie.setCost(cost);

                    System.out.println(dummie);
                    scheduler.addTask(dummie);
                    
                    time += MathUtil.getExpDistributed(1.00 / autoApAvgInterarrivalTime);

                    timeline.addEvent(new Timeline.Event(
                            Timeline.Event.EVTYPE_APTASK_RELEASE,
                            time,
                            scheduler.getTaskByName(dummie.getName())));
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error reading file");
            e.printStackTrace();
        }
    }
}
