package com.github.igorsuhorukov.jvmattachapi;

import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.*;

import com.sun.tools.attach.*;
import com.sun.tools.attach.spi.*;
import sun.tools.attach.*;

public final class VirtualMachineUtils
{
    static final boolean HOTSPOT_VM = System.getProperty("java.vm.name").contains("HotSpot") ||
            System.getProperty("java.vm.name").contains("OpenJDK");

    private static final AttachProvider ATTACH_PROVIDER = new AttachProvider() {
        @Override @Nullable public String name() { return null; }
        @Override @Nullable public String type() { return null; }
        @Override @Nullable public VirtualMachine attachVirtualMachine(String id) { return null; }
        @Override @Nullable public List<VirtualMachineDescriptor> listVirtualMachines() { return null; }
    };

    public static VirtualMachine connectToVirtualMachine(String processId) {
        VirtualMachine vm;
        if (AttachProvider.providers().isEmpty()) {
            if (HOTSPOT_VM) {
                vm = getVirtualMachine(processId);
            }
            else {
                String helpMessage = getHelpMessageForNonHotSpotVM();
                throw new IllegalStateException(helpMessage);
            }
        }
        else {
            vm = attachToRunningVM(processId);
        }
        return vm;
    }

    static VirtualMachine getVirtualMachine(String processId) {
        Class<? extends VirtualMachine> vmClass = findVirtualMachineClassAccordingToOS();
        Class<?>[] parameterTypes = {AttachProvider.class, String.class};

        try {
            // This is only done with Reflection to avoid the JVM pre-loading all the XyzVirtualMachine classes.
            Constructor<? extends VirtualMachine> vmConstructor = vmClass.getConstructor(parameterTypes);
            VirtualMachine newVM = vmConstructor.newInstance(ATTACH_PROVIDER, processId);
            return newVM;
        }
        catch (NoSuchMethodException e)     { throw new RuntimeException(e); }
        catch (InvocationTargetException e) { throw new RuntimeException(e); }
        catch (InstantiationException e)    { throw new RuntimeException(e); }
        catch (IllegalAccessException e)    { throw new RuntimeException(e); }
        catch (NoClassDefFoundError e) {
            throw new IllegalStateException("Native library for Attach API not available in this JRE", e);
        }
        catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("Native library for Attach API not available in this JRE", e);
        }
    }

    @Nonnull
    private static Class<? extends VirtualMachine> findVirtualMachineClassAccordingToOS()
    {
        if (File.separatorChar == '\\') {
            return WindowsVirtualMachine.class;
        }

        String osName = System.getProperty("os.name");

        if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
            return LinuxVirtualMachine.class;
        }
        else if (osName.startsWith("Mac OS X")) {
            return BsdVirtualMachine.class;
        }
        else if (osName.startsWith("Solaris")) {
            return SolarisVirtualMachine.class;
        }

        throw new IllegalStateException("Cannot use Attach API on unknown OS: " + osName);
    }

    @Nonnull
    private static String getHelpMessageForNonHotSpotVM()
    {
        String vmName = System.getProperty("java.vm.name");
        String helpMessage = "To run on " + vmName;

        if (vmName.contains("J9")) {
            helpMessage += ", add <IBM SDK>/lib/tools.jar to the runtime classpath (before jmockit), or";
        }

        return helpMessage + " use -javaagent:";
    }

    @Nonnull
    static VirtualMachine attachToRunningVM(String pid)
    {
        try {
            return VirtualMachine.attach(pid);
        }
        catch (AttachNotSupportedException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    public static String getProcessIdForRunningVM()
    {
        String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        int p = nameOfRunningVM.indexOf('@');
        return nameOfRunningVM.substring(0, p);
    }
}
