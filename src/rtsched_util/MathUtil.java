/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rtsched_util;

public class MathUtil {
    
    // code taken from Stack Overflow
    public static int getPoisson(double lambda) {
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= Math.random();
        } while (p > L);

        return k - 1;
    }
    
    public static int getExpDistributed(double lambda){
        double u = Math.random();
        
        System.out.println("Res = " + (Math.log(1.0 - u) / (-lambda)));
        
        return (int)(Math.log(1.0 - u) / (-lambda));
    }
    
}
