package ashjack.simukraftreloaded.common.life;

import java.io.Serializable;

/** a unit of a mall (or a shop) leased by a Sim to run their own business in (see Commerce) */
public class Lease implements Serializable
{
    private static final long serialVersionUID = 1L;

    public String owner = "";
    /** what they sell (Commerce.Line's name) */
    public String line = "";
    /** what they charge over cost: 1.2 to 2 */
    public float markup = 1.5f;
    /** today's takings, yesterday's, and in all */
    public float today, yesterday, total;
    public int salesToday;
    /** days in a row it couldn't pay its rent or hardly sold: three, and the lease is lost */
    public int strikes;
    /** the world day it opened */
    public long since;

    public String toLine()
    {
        return owner + ";" + line + ";" + markup + ";" + today + ";" + yesterday + ";" + total + ";" + salesToday + ";" + strikes + ";" + since;
    }

    public static Lease fromLine(String s)
    {
        try
        {
            String[] p = s.split(";");
            Lease l = new Lease();
            l.owner = p[0];
            l.line = p[1];
            l.markup = Float.parseFloat(p[2]);
            l.today = Float.parseFloat(p[3]);
            l.yesterday = Float.parseFloat(p[4]);
            l.total = Float.parseFloat(p[5]);
            l.salesToday = Integer.parseInt(p[6]);
            l.strikes = Integer.parseInt(p[7]);
            l.since = Long.parseLong(p[8]);
            return l;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
