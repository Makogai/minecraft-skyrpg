import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redefines a set of classes in a running JVM in one atomic step (JDI redefineClasses), so the game
 * never runs a mix of old and new classes (jdb's "redefine" does one class at a time, and a new caller
 * swapped in before its new callee crashes the game with NoSuchMethodError).
 *
 *   java AtomicRedefine.java <port> <list-file>     list lines: "<binary class name> <class file path>"
 *
 * Prints "redefined N", "not loaded <name>" per class the game hasn't loaded, or "error <message>".
 */
public class AtomicRedefine {
    public static void main(String[] args) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(args[0]);
        VirtualMachine vm = connector.attach(arguments);
        try {
            Map<ReferenceType, byte[]> classes = new HashMap<>();
            for (String line : Files.readAllLines(Path.of(args[1]))) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" ", 2);
                List<ReferenceType> loaded = vm.classesByName(parts[0]);
                if (loaded.isEmpty()) {
                    System.out.println("not loaded " + parts[0]);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(Path.of(parts[1]));
                for (ReferenceType type : loaded) {
                    classes.put(type, bytes);
                }
            }
            if (!classes.isEmpty()) {
                vm.redefineClasses(classes);
            }
            System.out.println("redefined " + classes.size());
        } catch (Throwable t) {
            System.out.println("error " + t);
        } finally {
            vm.dispose();
        }
    }
}
