package rtsched_util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.text.SimpleDateFormat;

public class Log {

	private static String logFileName;
    private static String resFileName;
	private static boolean appending;
    private static boolean resAppending;
    
	private static boolean printOnScreen;
	static Calendar cal;
	static SimpleDateFormat sdf;
	
	public static void init(String logFileName, 
            String resFileName,
            boolean printOnScreen) {
		Log.logFileName = logFileName;
		Log.appending = false;
        Log.resFileName = resFileName;
        Log.resAppending = false;
		Log.printOnScreen = printOnScreen;
		Log.sdf = new SimpleDateFormat("HH:mm:ss:SSS");
	}
	
	public static void println(String textToPrint){
		Log.cal = Calendar.getInstance();
		String time = sdf.format(cal.getTime());
		if (Log.printOnScreen){
			System.out.println("[" + time + "] " + textToPrint);
		}
		else {
//			System.out.println("Printing to log file...");
		}
		try {
			
			BufferedWriter bw = new BufferedWriter( 
					new FileWriter(logFileName,appending)
					);
			bw.append(time + " | " +textToPrint + "\n");
			bw.close();
		}
		catch(IOException e) {
			System.err.println("Error writing to log file");
			e.printStackTrace();
		}
		Log.appending = true;
	}

    
    public static void resPrintln(String textToPrint) {
		try {
			BufferedWriter bw = new BufferedWriter( 
					new FileWriter(resFileName, resAppending)
					);
			bw.append(textToPrint + "\n");
			bw.close();
		}
		catch(IOException e) {
			System.err.println("Error writing to log file" + e);
		}
		Log.resAppending = true;
    }
	
}
