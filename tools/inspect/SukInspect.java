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
            System.out.println("usage: SukInspect <port> folks|folk|buildings|boxes|states|get|set|cmd|path ...");
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
                case "path": status = rest.isEmpty() ? 1 : path(rest); break;
                case "block": status = rest.size() < 3 ? 1 : block(Integer.parseInt(rest.get(0)), Integer.parseInt(rest.get(1)), Integer.parseInt(rest.get(2)),
                        rest.size() > 3 ? Integer.parseInt(rest.get(3)) : 1); break;
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
        final String cmd = command.startsWith("/") ? command.substring(1) : command;
        return onServerThread(thread -> {
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
            Value result = manager.invokeMethod(thread, execute, Arrays.asList(server, vm.mirrorOf(cmd)),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            System.out.println("Ran /" + cmd + " (result " + s(result) + ")");
            return 0;
        });
    }

    /**
     * Ask Sim-U-Kraft's pathfinder for a route from a Sim to x,y,z (default: their workplace), and draw it on a map of
     * the blocks at their feet level. Also shows the path the Sim is following right now, if any.
     */
    static int path(List<String> args) throws Exception
    {
        int split = args.size();
        while (split > 0 && args.get(split - 1).matches("-?\\d+"))
        {
            split--;
        }
        ObjectReference folk = findFolk(String.join(" ", args.subList(0, split)));
        if (folk == null)
        {
            System.out.println("No Sim called " + String.join(" ", args.subList(0, split)));
            return 1;
        }
        final ObjectReference entity = (ObjectReference) field(folk, "theEntity");
        if (entity == null)
        {
            System.out.println(s(field(folk, "name")) + " isn't in the world right now");
            return 1;
        }
        final int[] target = new int[3];
        if (args.size() - split >= 3)
        {
            for (int i = 0; i < 3; i++)
            {
                target[i] = Integer.parseInt(args.get(split + i));
            }
        }
        else
        {
            ObjectReference at = (ObjectReference) field(folk, "employedAt");
            if (at == null)
            {
                System.out.println(s(field(folk, "name")) + " has no workplace; give x y z");
                return 1;
            }
            target[0] = (int) Double.parseDouble(s(field(at, "x")));
            target[1] = (int) Double.parseDouble(s(field(at, "y")));
            target[2] = (int) Double.parseDouble(s(field(at, "z")));
        }
        final int sx = (int) Math.floor(Double.parseDouble(s(fieldAny(entity, "posX", "field_70165_t"))));
        final int sy = (int) Math.floor(Double.parseDouble(s(fieldAny(entity, "posY", "field_70163_u"))) + 0.1);
        final int sz = (int) Math.floor(Double.parseDouble(s(fieldAny(entity, "posZ", "field_70161_v"))));

        ObjectReference nav = (ObjectReference) fieldAny(entity, "navigator", "field_70699_by");
        ObjectReference live = nav == null ? null : (ObjectReference) fieldAny(nav, "currentPath", "field_75514_c");
        final List<int[]> livePoints = points(live);

        return onServerThread(thread -> {
            ClassType finder = (ClassType) findClass(PKG + "common.FolkPathfinder");
            Method find = method(finder, "(Lnet/minecraft/entity/Entity;III)Lnet/minecraft/pathfinding/PathEntity;", "findPath");
            ObjectReference result = (ObjectReference) finder.invokeMethod(thread, find,
                    Arrays.asList(entity, vm.mirrorOf(target[0]), vm.mirrorOf(target[1]), vm.mirrorOf(target[2])),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            List<int[]> planned = points(result);

            System.out.println("From " + sx + "," + sy + "," + sz + " to " + target[0] + "," + target[1] + "," + target[2]);
            System.out.println("Planned now: " + describe(planned));
            System.out.println("Following:   " + describe(livePoints));

            // map of the feet level, around the start, the target and the path
            int x0 = Math.min(sx, target[0]) - 6, x1 = Math.max(sx, target[0]) + 6;
            int z0 = Math.min(sz, target[2]) - 6, z1 = Math.max(sz, target[2]) + 6;
            for (int[] p : planned)
            {
                x0 = Math.min(x0, p[0] - 2); x1 = Math.max(x1, p[0] + 2);
                z0 = Math.min(z0, p[2] - 2); z1 = Math.max(z1, p[2] + 2);
            }
            x1 = Math.min(x1, x0 + 70);
            z1 = Math.min(z1, z0 + 50);
            ObjectReference world = (ObjectReference) fieldAny(entity, "worldObj", "field_70170_p");
            Method getBlock = method((ClassType) world.referenceType(), "(III)Lnet/minecraft/block/Block;", "getBlock", "func_147439_a");
            java.util.Map<Long, Character> cache = new java.util.HashMap<Long, Character>();
            System.out.println("Map at y=" + sy + " (feet) / " + (sy + 1) + " (head); x " + x0 + ".." + x1 + " left to right, z " + z0 + ".." + z1 + " top to bottom");
            System.out.println("  S start  T target  * planned path  o path being followed  # solid  F fence/wall  D door  G gate  ~ water  . open  _ open (low ceiling)");
            for (int z = z0; z <= z1; z++)
            {
                StringBuilder row = new StringBuilder(String.format("%5d ", z));
                for (int x = x0; x <= x1; x++)
                {
                    char c;
                    if (x == sx && z == sz) c = 'S';
                    else if (x == target[0] && z == target[2]) c = 'T';
                    else if (on(planned, x, z)) c = '*';
                    else if (on(livePoints, x, z)) c = 'o';
                    else
                    {
                        char feet = classify(thread, world, getBlock, cache, x, sy, z);
                        char head = classify(thread, world, getBlock, cache, x, sy + 1, z);
                        c = feet == '.' && head != '.' ? (head == '#' ? '_' : head) : feet;
                    }
                    row.append(c);
                }
                System.out.println(row);
            }
            return 0;
        });
    }

    /** the blocks in a column, from y up: class, name, metadata and collision box height */
    static int block(final int x, final int y, final int z, final int count) throws Exception
    {
        return onServerThread(thread -> {
            ClassType serverType = (ClassType) findClass("net.minecraft.server.MinecraftServer");
            ObjectReference server = (ObjectReference) serverType.invokeMethod(thread,
                    method(serverType, "()Lnet/minecraft/server/MinecraftServer;", "getServer", "func_71276_C"),
                    Collections.emptyList(), ObjectReference.INVOKE_SINGLE_THREADED);
            ObjectReference world = (ObjectReference) ((ArrayReference) fieldAny(server, "worldServers", "field_71305_c")).getValue(0);
            ClassType wt = (ClassType) world.referenceType();
            Method getBlock = method(wt, "(III)Lnet/minecraft/block/Block;", "getBlock", "func_147439_a");
            Method getMeta = method(wt, "(III)I", "getBlockMetadata", "func_72805_g");
            for (int yy = y + count - 1; yy >= y; yy--)
            {
                List<Value> at = Arrays.asList(vm.mirrorOf(x), vm.mirrorOf(yy), vm.mirrorOf(z));
                ObjectReference b = (ObjectReference) world.invokeMethod(thread, getBlock, at, ObjectReference.INVOKE_SINGLE_THREADED);
                Value meta = world.invokeMethod(thread, getMeta, at, ObjectReference.INVOKE_SINGLE_THREADED);
                Method box = method((ClassType) b.referenceType(),
                        "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;", "getCollisionBoundingBoxFromPool", "func_149668_a");
                ObjectReference bb = (ObjectReference) b.invokeMethod(thread, box, Arrays.asList(world, at.get(0), at.get(1), at.get(2)),
                        ObjectReference.INVOKE_SINGLE_THREADED);
                String h = bb == null ? "none" : String.format("%.2f", Double.parseDouble(s(fieldAny(bb, "maxY", "field_72337_e"))) - yy);
                System.out.println(x + "," + yy + "," + z + "  " + b.referenceType().name() + "  "
                        + s(fieldAny(b, "unlocalizedName", "field_149770_b")) + "  meta " + s(meta) + "  collision top " + h);
            }
            return 0;
        });
    }

    static char classify(ThreadReference thread, ObjectReference world, Method getBlock, java.util.Map<Long, Character> cache,
            int x, int y, int z) throws Exception
    {
        ObjectReference block = (ObjectReference) world.invokeMethod(thread, getBlock,
                Arrays.asList(vm.mirrorOf(x), vm.mirrorOf(y), vm.mirrorOf(z)), ObjectReference.INVOKE_SINGLE_THREADED);
        Character c = cache.get(block.uniqueID());
        if (c == null)
        {
            String n = block.referenceType().name().toLowerCase();
            String id = s(fieldAny(block, "unlocalizedName", "field_149770_b")).toLowerCase();
            if (n.endsWith("blockair")) c = '.';
            else if (n.contains("door") && !n.contains("trap")) c = 'D';
            else if (n.contains("fencegate") || n.contains("gate")) c = 'G';
            else if (n.contains("fence") || n.contains("wall") || n.contains("pane")) c = 'F';
            else if (n.contains("liquid") || id.contains("water")) c = '~';
            else if (n.contains("flower") || n.contains("tallgrass") || n.contains("doubleplant") || n.contains("torch")
                    || n.contains("carpet") || n.contains("sapling") || n.contains("rail") || n.contains("button")
                    || n.contains("lever") || n.contains("sign") || n.contains("snow") || n.contains("pressure")
                    || n.contains("redstonewire") || n.contains("ladder") || n.contains("vine") || n.contains("bush")) c = '.';
            else c = '#';
            cache.put(block.uniqueID(), c);
        }
        return c;
    }

    /** the points of a PathEntity, as x,y,z */
    static List<int[]> points(ObjectReference pathEntity)
    {
        List<int[]> out = new ArrayList<int[]>();
        if (pathEntity == null)
        {
            return out;
        }
        for (Value v : list(fieldAny(pathEntity, "points", "field_75884_a")))
        {
            ObjectReference p = (ObjectReference) v;
            out.add(new int[] {Integer.parseInt(s(fieldAny(p, "xCoord", "field_75839_a"))),
                    Integer.parseInt(s(fieldAny(p, "yCoord", "field_75837_b"))),
                    Integer.parseInt(s(fieldAny(p, "zCoord", "field_75838_c")))});
        }
        return out;
    }

    static String describe(List<int[]> points)
    {
        if (points.isEmpty())
        {
            return "none";
        }
        StringBuilder sb = new StringBuilder(points.size() + " steps:");
        for (int[] p : points)
        {
            sb.append(' ').append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
        }
        return sb.toString();
    }

    static boolean on(List<int[]> points, int x, int z)
    {
        for (int[] p : points)
        {
            if (p[0] == x && p[2] == z)
            {
                return true;
            }
        }
        return false;
    }

    interface ServerWork
    {
        int run(ThreadReference thread) throws Exception;
    }

    /** wait for the server thread to reach Sim-U-Kraft's tick handler, hold it there while work runs, then let it go */
    static int onServerThread(ServerWork work) throws Exception
    {
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
            return work.run(thread);
        }
        catch (InvocationException e)
        {
            System.out.println("The game threw " + e.exception().referenceType().name());
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

    /** a field by its dev name or its SRG name (the game runs with SRG names) */
    static Value fieldAny(ObjectReference o, String... names)
    {
        for (String n : names)
        {
            Field f = o.referenceType().fieldByName(n);
            if (f != null)
            {
                return o.getValue(f);
            }
        }
        return null;
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
