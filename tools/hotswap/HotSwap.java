import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes changed .class files into a running game through its JDWP debug port.
 *
 * Usage: java HotSwap.java <port> <classes dir> <class name>...
 * Class names are dotted (ashjack.simukraftreloaded.Foo$1); the files are read from the classes dir.
 * Classes the game hasn't loaded yet are skipped: the game already has the old jar open, so they will load the
 * old version until the next restart.
 * Exit code 0 = swapped (or nothing to swap), 2 = can't connect, 3 = the JVM refused the change.
 */
public class HotSwap
{
    public static void main(String[] args) throws Exception
    {
        int port = Integer.parseInt(args[0]);
        Path dir = Paths.get(args[1]);

        VirtualMachine vm;
        try
        {
            vm = attach(port);
        }
        catch (Exception e)
        {
            System.out.println("Can't reach the game on port " + port + " (" + e.getMessage() + ") - is it running?");
            System.exit(2);
            return;
        }

        Map<ReferenceType, byte[]> swaps = new HashMap<>();
        List<String> notLoaded = new ArrayList<>();
        for (int i = 2; i < args.length; i++)
        {
            String name = args[i];
            List<ReferenceType> loaded = vm.classesByName(name);
            if (loaded.isEmpty())
            {
                notLoaded.add(name);
                continue;
            }
            byte[] bytes = Files.readAllBytes(dir.resolve(name.replace('.', '/') + ".class"));
            for (ReferenceType t : loaded)
            {
                swaps.put(t, bytes);
            }
        }

        int code = 0;
        try
        {
            if (!swaps.isEmpty())
            {
                vm.redefineClasses(swaps);
            }
            System.out.println("Swapped " + swaps.size() + " class(es) into the running game");
        }
        catch (UnsupportedOperationException e)
        {
            System.out.println("The game refused the swap: only changes inside existing methods can be swapped.");
            System.out.println("Added/removed fields, methods or a changed class header need a restart. (" + e.getMessage() + ")");
            code = 3;
        }
        catch (Throwable e)
        {
            System.out.println("The game refused the swap: " + e);
            code = 3;
        }
        if (!notLoaded.isEmpty())
        {
            System.out.println("Not loaded yet (they will load the OLD version until a restart): " + String.join(", ", notLoaded));
        }
        vm.dispose(); // detach; the game keeps running
        System.exit(code);
    }

    private static VirtualMachine attach(int port) throws Exception
    {
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors())
        {
            if (c.transport().name().equals("dt_socket"))
            {
                Map<String, Connector.Argument> a = c.defaultArguments();
                a.get("hostname").setValue("127.0.0.1");
                a.get("port").setValue(Integer.toString(port));
                a.get("timeout").setValue("5000");
                return c.attach(a);
            }
        }
        throw new IllegalStateException("no socket connector");
    }
}
