import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Looks inside the running test pack through its JDWP debug port (see suk-inspect.sh for the commands).
 *
 * Reading never stops the game: field values are read while it runs. "cmd" is the exception: it waits for the
 * server thread to reach Sim-U-Kraft's tick handler, holds it there for the moment it takes to run the command as
 * the server console, then lets it go.
 *
 * Usage: java SukInspect.java <port> <command> [args...]
 * Exit code 0 = ok, 1 = bad usage or not found, 2 = can't reach the game, 3 = the command failed.
 */
public class SukInspect
{
    static final String PKG = "ashjack.simukraftreloaded.";
    static VirtualMachine vm;

    public static void main(String[] args) throws Exception
    {
        if (args.length < 2)
        {
            System.out.println("usage: SukInspect <port> folks|folk|buildings|boxes|states|get|set|cmd ...");
            System.exit(1);
        }
        try
        {
            vm = attach(Integer.parseInt(args[0]));
        }
        catch (Exception e)
        {
            System.out.println("Can't reach the game on port " + args[0] + " (" + e.getMessage() + ") - is it running?");
            System.exit(2);
        }

        int status = 0;
        try
        {
            String cmd = args[1];
            List<String> rest = Arrays.asList(args).subList(2, args.length);
            switch (cmd)
            {
                case "folks": folks(rest.isEmpty() ? null : String.join(" ", rest).toLowerCase()); break;
                case "folk": status = folk(String.join(" ", rest)); break;
                case "buildings": buildings(); break;
                case "boxes": boxes(); break;
                case "states": dump(staticValue("core.registry.SimukraftReloaded", "serverStates"), ""); break;
                case "get": status = get(String.join(" ", rest)); break;
                case "set": status = rest.size() < 2 ? 1 : set(rest.get(0), String.join(" ", rest.subList(1, rest.size()))); break;
                case "cmd": status = runCommand(String.join(" ", rest)); break;
                default:
                    System.out.println("unknown command: " + cmd);
                    status = 1;
            }
        }
        finally
        {
            vm.dispose();
        }
        System.exit(status);
    }

    // ---------------------------------------------------------------- commands

    static void folks(String filter)
    {
        List<Value> all = list(staticValue("core.registry.SimukraftReloaded", "serverFolks"));
        System.out.printf("%-24s %3s %-12s %-14s %-13s %4s  %-16s %s%n", "NAME", "AGE", "JOB", "WORKS AT", "ACTION", "MOOD", "AT", "DOING");
        for (Value v : all)
        {
            ObjectReference f = (ObjectReference) v;
            String name = s(field(f, "name"));
            String job = s(field(f, "vocation"));
            if (filter != null && !name.toLowerCase().contains(filter) && !job.toLowerCase().contains(filter))
            {
                continue;
            }
            System.out.printf("%-24s %3s %-12s %-14s %-13s %4s  %-16s %s%n", name, s(field(f, "age")), job,
                    s(field(f, "employedAt")), s(field(f, "action")), s(field(f, "happiness")),
                    s(field(f, "location")), s(field(f, "statusText")));
        }
        System.out.println(all.size() + " Sims");
    }

    static int folk(String name)
    {
        ObjectReference f = findFolk(name);
        if (f == null)
        {
            System.out.println("No Sim matching \"" + name + "\"");
            return 1;
        }
        dump(f, "");
        Value job = field(f, "theirJob");
        if (job instanceof ObjectReference)
        {
            System.out.println("\n-- job: " + simpleName(((ObjectReference) job).referenceType().name()));
            dump((ObjectReference) job, "");
        }
        return 0;
    }

    static void buildings()
    {
        List<Value> all = list(staticValue("core.registry.SimukraftReloaded", "serverBuildings"));
        System.out.printf("%-28s %-12s %-14s %-14s %-8s %s%n", "NAME", "TYPE", "AT", "CONBOX", "DONE", "TENANTS");
        for (Value v : all)
        {
            ObjectReference b = (ObjectReference) v;
            System.out.printf("%-28s %-12s %-14s %-14s %-8s %s%n", s(field(b, "displayNameWithoutPK")), s(field(b, "type")),
                    s(field(b, "primaryXYZ")), s(field(b, "conBoxLocation")), s(field(b, "buildingComplete")),
                    s(field(b, "tennants")));
        }
        System.out.println(all.size() + " buildings");
    }

    static void boxes()
    {
        for (String which : new String[] {"serverMiningBoxes", "serverFarmingBoxes"})
        {
            List<Value> all = list(staticValue("core.registry.SimukraftReloaded", which));
            System.out.println("-- " + which.replace("server", "") + ": " + all.size());
            for (Value v : all)
            {
                dump((ObjectReference) v, "  ");
                System.out.println();
            }
        }
    }

    /** get Class.staticField[.field|[index]]... e.g. SimukraftReloaded.serverStates.credits or SimukraftReloaded.serverFolks[0] */
    static int get(String path)
    {
        Value[] out = new Value[1];
        String problem = walk(path, out);
        if (problem != null)
        {
            System.out.println(problem);
            return 1;
        }
        Value cur = out[0];
        if (cur instanceof ObjectReference && !isLeaf((ObjectReference) cur))
        {
            dump(cur, "");
        }
        else
        {
            System.out.println(s(cur));
        }
        return 0;
    }

    /** follow a get-style path; puts the value in out[0] and returns null, or returns what went wrong */
    static String walk(String path, Value[] out)
    {
        String[] parts = path.split("\\.");
        ReferenceType type = null;
        int i = 0;
        StringBuilder cls = new StringBuilder();
        // the longest prefix that names a loaded class
        for (int j = 0; j < parts.length && type == null; j++)
        {
            cls.append(j == 0 ? "" : ".").append(parts[j]);
            type = findClass(cls.toString());
            i = j + 1;
        }
        if (type == null || i >= parts.length)
        {
            return "Couldn't find a class and static field in \"" + path + "\"";
        }
        Value cur = null;
        for (int first = i; i < parts.length; i++)
        {
            String part = parts[i];
            Integer index = null;
            int br = part.indexOf('[');
            if (br >= 0)
            {
                index = Integer.parseInt(part.substring(br + 1, part.indexOf(']')));
                part = part.substring(0, br);
            }
            if (i == first)
            {
                Field fl = type.fieldByName(part);
                if (fl == null || !fl.isStatic())
                {
                    return type.name() + " has no static field " + part;
                }
                cur = type.getValue(fl);
            }
            else if (cur instanceof ObjectReference)
            {
                cur = field((ObjectReference) cur, part);
            }
            else
            {
                return "Can't go into " + part + ": the value before it is " + s(cur);
            }
            if (index != null)
            {
                List<Value> l = list(cur);
                if (index >= l.size())
                {
                    return part + " has only " + l.size() + " entries";
                }
                cur = l.get(index);
            }
        }
        out[0] = cur;
        return null;
    }

    /** set a number, boolean or string field: set SimukraftReloaded.serverStates.credits 148.67 */
    static int set(String path, String text) throws Exception
    {
        int dot = path.lastIndexOf('.');
        String owner = path.substring(0, dot), name = path.substring(dot + 1);
        ObjectReference obj = null;
        ReferenceType type = findClass(owner);
        if (type == null)
        {
            Value[] o = new Value[1];
            String problem = walk(owner, o);
            if (problem != null || !(o[0] instanceof ObjectReference))
            {
                System.out.println(problem != null ? problem : owner + " is " + s(o[0]));
                return 1;
            }
            obj = (ObjectReference) o[0];
            type = obj.referenceType();
        }
        Field f = type.fieldByName(name);
        if (f == null)
        {
            System.out.println(type.name() + " has no field " + name);
            return 1;
        }
        Value v;
        switch (f.typeName())
        {
            case "int": v = vm.mirrorOf(Integer.parseInt(text)); break;
            case "long": v = vm.mirrorOf(Long.parseLong(text)); break;
            case "float": v = vm.mirrorOf(Float.parseFloat(text)); break;
            case "double": v = vm.mirrorOf(Double.parseDouble(text)); break;
            case "boolean": v = vm.mirrorOf(Boolean.parseBoolean(text)); break;
            case "java.lang.String": v = vm.mirrorOf(text); break;
            default:
                System.out.println("Can only set numbers, booleans and strings (" + name + " is " + f.typeName() + ")");
                return 1;
        }
        if (obj == null)
        {
            ((ClassType) type).setValue(f, v);
        }
        else
        {
            obj.setValue(f, v);
        }
        System.out.println(path + " = " + s(v));
        return 0;
    }

    /** run a command as the server console, on the server thread */
    static int runCommand(String command) throws Exception
    {
        if (command.startsWith("/"))
        {
            command = command.substring(1);
        }
        ClassType handler = (ClassType) findClass(PKG + "common.CommonTickHandler");
        Method tick = null;
        for (Method m : handler.methodsByName("tick"))
        {
            if (m.signature().contains("ServerTickEvent"))
            {
                tick = m;
            }
        }
        if (tick == null)
        {
            System.out.println("Couldn't find Sim-U-Kraft's server tick handler");
            return 3;
        }

        BreakpointRequest bp = vm.eventRequestManager().createBreakpointRequest(tick.location());
        bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        bp.enable();
        EventSet set = null;
        ThreadReference thread = null;
        long until = System.currentTimeMillis() + 5000;
        try
        {
            while (thread == null && System.currentTimeMillis() < until)
            {
                EventSet es = vm.eventQueue().remove(500);
                if (es == null)
                {
                    continue;
                }
                for (Event e : es)
                {
                    if (e instanceof BreakpointEvent)
                    {
                        set = es;
                        thread = ((BreakpointEvent) e).thread();
                    }
                }
                if (thread == null)
                {
                    es.resume();
                }
            }
        }
        finally
        {
            bp.disable();
            vm.eventRequestManager().deleteEventRequest(bp);
        }
        if (thread == null)
        {
            System.out.println("The server didn't tick within 5 seconds - is the game paused or still loading the world?");
            return 3;
        }

        try
        {
            ClassType serverType = (ClassType) findClass("net.minecraft.server.MinecraftServer");
            Method getServer = method(serverType, "()Lnet/minecraft/server/MinecraftServer;", "getServer", "func_71276_C");
            ObjectReference server = (ObjectReference) serverType.invokeMethod(thread, getServer, Collections.emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            Method getManager = method((ClassType) server.referenceType(), "()Lnet/minecraft/command/ICommandManager;",
                    "getCommandManager", "func_71187_D");
            ObjectReference manager = (ObjectReference) server.invokeMethod(thread, getManager, Collections.emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            Method execute = method((ClassType) manager.referenceType(),
                    "(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;)I", "executeCommand", "func_71556_a");
            Value result = manager.invokeMethod(thread, execute, Arrays.asList(server, vm.mirrorOf(command)),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            System.out.println("Ran /" + command + " (result " + s(result) + ")");
            return 0;
        }
        catch (InvocationException e)
        {
            System.out.println("/" + command + " threw " + e.exception().referenceType().name());
            return 3;
        }
        finally
        {
            set.resume();
        }
    }

    // ---------------------------------------------------------------- helpers

    static VirtualMachine attach(int port) throws Exception
    {
        AttachingConnector socket = null;
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors())
        {
            if (c.name().equals("com.sun.jdi.SocketAttach"))
            {
                socket = c;
            }
        }
        Map<String, Connector.Argument> a = socket.defaultArguments();
        a.get("hostname").setValue("127.0.0.1");
        a.get("port").setValue(String.valueOf(port));
        return socket.attach(a);
    }

    /** a loaded class by full name, short name inside the mod (SimukraftReloaded), or mod-relative name (core.registry.SimukraftReloaded) */
    static ReferenceType findClass(String name)
    {
        for (String candidate : new String[] {name, PKG + name})
        {
            List<ReferenceType> l = vm.classesByName(candidate);
            if (!l.isEmpty())
            {
                return l.get(0);
            }
        }
        if (name.contains("."))
        {
            return null;
        }
        for (ReferenceType t : vm.allClasses())
        {
            if (t.name().startsWith(PKG) && simpleName(t.name()).equals(name))
            {
                return t;
            }
        }
        return null;
    }

    static Value staticValue(String cls, String fieldName)
    {
        ReferenceType t = findClass(PKG + cls);
        return t.getValue(t.fieldByName(fieldName));
    }

    static Method method(ClassType type, String signature, String... names)
    {
        for (String n : names)
        {
            Method m = type.concreteMethodByName(n, signature);
            if (m != null)
            {
                return m;
            }
        }
        throw new IllegalStateException("no method " + Arrays.toString(names) + signature + " on " + type.name());
    }

    static Value field(ObjectReference o, String name)
    {
        Field f = o.referenceType().fieldByName(name);
        return f == null ? null : o.getValue(f);
    }

    static ObjectReference findFolk(String name)
    {
        String want = name.toLowerCase();
        ObjectReference partial = null;
        for (Value v : list(staticValue("core.registry.SimukraftReloaded", "serverFolks")))
        {
            String n = s(field((ObjectReference) v, "name")).toLowerCase();
            if (n.equals(want))
            {
                return (ObjectReference) v;
            }
            if (partial == null && n.contains(want))
            {
                partial = (ObjectReference) v;
            }
        }
        return partial;
    }

    static List<Value> list(Value v)
    {
        if (!(v instanceof ObjectReference))
        {
            return Collections.emptyList();
        }
        ObjectReference o = (ObjectReference) v;
        if (o instanceof ArrayReference)
        {
            return ((ArrayReference) o).getValues();
        }
        Value size = field(o, "size");
        Value data = field(o, "elementData");
        if (size instanceof IntegerValue && data instanceof ArrayReference)
        {
            int n = ((IntegerValue) size).value();
            return n == 0 ? new ArrayList<Value>() : ((ArrayReference) data).getValues(0, n);
        }
        return Collections.emptyList();
    }

    /** every instance field of an object, one per line */
    static void dump(Value v, String indent)
    {
        if (!(v instanceof ObjectReference))
        {
            System.out.println(indent + s(v));
            return;
        }
        ObjectReference o = (ObjectReference) v;
        for (Field f : o.referenceType().allFields())
        {
            if (f.isStatic())
            {
                continue;
            }
            System.out.println(indent + f.name() + " = " + s(o.getValue(f)));
        }
    }

    static boolean isLeaf(ObjectReference o)
    {
        String t = o.referenceType().name();
        return o instanceof StringReference || t.startsWith("java.lang.") || t.endsWith("$V3")
                || (o.referenceType() instanceof ClassType && ((ClassType) o.referenceType()).isEnum());
    }

    static String simpleName(String n)
    {
        return n.substring(n.lastIndexOf('.') + 1);
    }

    /** a short readable form of a value */
    static String s(Value v)
    {
        if (v == null)
        {
            return "null";
        }
        if (v instanceof StringReference)
        {
            return ((StringReference) v).value();
        }
        if (v instanceof PrimitiveValue)
        {
            return v.toString();
        }
        ObjectReference o = (ObjectReference) v;
        String t = o.referenceType().name();
        if (t.endsWith("$V3"))
        {
            return num(field(o, "x")) + "," + num(field(o, "y")) + "," + num(field(o, "z"))
                    + (s(field(o, "theDimension")).equals("0") ? "" : " dim " + s(field(o, "theDimension")));
        }
        if (t.startsWith("java.lang.") && field(o, "value") != null)
        {
            return s(field(o, "value"));
        }
        if (o.referenceType() instanceof ClassType && ((ClassType) o.referenceType()).isEnum())
        {
            return s(field(o, "name"));
        }
        if (t.equals("java.util.ArrayList") || o instanceof ArrayReference)
        {
            List<Value> l = list(o);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size() && i < 8; i++)
            {
                Value e = l.get(i);
                sb.append(i == 0 ? "" : ", ").append(e instanceof ObjectReference && !isLeaf((ObjectReference) e) ? label((ObjectReference) e) : s(e));
            }
            return sb.append(l.size() > 8 ? ", ... " + l.size() + " total]" : "]").toString();
        }
        return label(o);
    }

    /** a mod object as its name if it has one, otherwise its class */
    static String label(ObjectReference o)
    {
        for (String n : new String[] {"name", "displayNameWithoutPK", "displayName"})
        {
            Value v = field(o, n);
            if (v instanceof StringReference)
            {
                return simpleName(o.referenceType().name()) + "(" + ((StringReference) v).value() + ")";
            }
        }
        return simpleName(o.referenceType().name());
    }

    /** 481.0 -> 481, 456.92 -> 456.9 */
    static String num(Value v)
    {
        double d = Double.parseDouble(s(v));
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.1f", d);
    }
}
