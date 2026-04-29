package compiler;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println("✓ FEATURE 1: Typo Detection");
        System.out.println("Try: 'nt a = 10;' → Suggests 'int'");
        System.out.println("");
        System.out.println("✓ FEATURE 2: Ternary Error Suppression");
        System.out.println("Try: 'String x = (a < b) ? \"str\" : 50;'");
        System.out.println("Reports only ONE error (incompatible branches)");
        System.out.println("Suppresses the follow-up initialization error.");
    }
}
