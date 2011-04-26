package freenet.support;

import com.sun.jna.Platform;
import com.sun.jna.Native;
import com.sun.jna.Library;
import com.sun.jna.win32.StdCallLibrary;

public class ProcessPriority {
    private static boolean inited = false;
    private static boolean background = false;
    
    public enum Priority {
        NormalPriority;
        LowerPriority;
        BackgroundPriority;
        IdlePriority;
    };
    
    /// Windows interface (kernel32.dll) ///

    private interface Kernel32 extends StdCallLibrary {
        /* HANDLE -> Pointer, DWORD -> int */
        public Pointer GetCurrentProcess();
        public int GetPriorityClass(Pointer hProcess);
        public boolean SetPriorityClass(Pointer hProcess, int dwPriorityClass);
        public int GetLastError();

        public static int REALTIME_PRIORITY_CLASS               = 0x00000100;
        public static int HIGH_PRIORITY_CLASS                   = 0x00000080;
        public static int ABOVE_NORMAL_PRIORITY_CLASS           = 0x00008000;
        public static int NORMAL_PRIORITY_CLASS                 = 0x00000020;
        public static int BELOW_NORMAL_PRIORITY_CLASS           = 0x00004000;
        public static int IDLE_PRIORITY_CLASS                   = 0x00000040;
        public static int PROCESS_MODE_BACKGROUND_BEGIN         = 0x00100000;
        public static int PROCESS_MODE_BACKGROUND_END           = 0x00200000;
        
        public static int ERROR_PROCESS_MODE_ALREADY_BACKGROUND = 402;
        public static int ERROR_PROCESS_MODE_NOT_BACKGROUND     = 403;
    }

    private static Kernel32 win = null;
    
    /// Linux interface (/lib/libc.so.6) ///
    
    private interface libc extends Library {
        public int getpriority(int which, int who);
        public int setpriority(int which, int who, int prio);
        
        public static int PRIO_PROCESS = 0; // defined in include/linux/resource.h
    }
    
    private static libc lin = null;
    
    private static final int linBackgroundPrio = 14;
    private static int linOldPrio = linBackgroundPrio;
    
    /// Implementation

    private static boolean init() {
        if (!inited) {
            try {
                if (Platform.isWindows()) {
                    win = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
                    inited = true;
                } else if (Platform.isLinux()) {
                    lin = (libc) Native.loadLibrary("c", libc.class);
                    inited = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return inited;
    }
    
    private static int getNativeProcessPriority() {
        if (win)
            return win.GetPriorityClass(win.GetCurrentProcess());
        if (lin)
            return lin.getpriority(libc.PRIO_PROCESS, 0);
    }
    
    private static boolean setNativeProcessPriority(int prio) {
        if (win)
            return win.SetPriorityClass(win.GetCurrentProcess(), prio);
        if (lin)
            return lin.setpriority(libc.PRIO_PROCESS, 0, prio) == 0;
    }
    
    private static boolean enterBackgroundMode() throws Exception {
        if (!background) {
            if (win) {
                if (setNativeProcessPriority(Kernel32.PROCESS_MODE_BACKGROUND_BEGIN))
                    background = true;
                else if (win.GetLastError() == Kernel32.ERROR_PROCESS_MODE_ALREADY_BACKGROUND)
                    throw new Exception("Illegal state");
            } else if (lin) {
                int oldprio = getNativeProcessPriority();
                if (oldprio != linBackgroundPrio) {
                    linOldPrio = oldprio;
                    if (setNativeProcessPriority(linBackgroundPrio))
                        background = true;
                }
            }
        }
        return background;
    }

    private static boolean exitBackgroundMode() throws Exception {
        if (background) {
            if (win) {
                if (setNativeProcessPriority(Kernel32.PROCESS_MODE_BACKGROUND_END)
                    background = false;
                else if (win.GetLastError() == Kernel32.ERROR_PROCESS_MODE_NOT_BACKGROUND)
                    throw new Exception("Illegal state");
            } else if (lin) {
                if (linOldPrio != linBackgroundPrio)
                    if (setNativeProcessPriority(linOldPrio))
                        background = false;
            }
        }
        return background;
    }
    
    /// Public methods ///////////////////////////////////
    
    public static Priority get() {
        if (!init())
            return false;

        if (background)
            return Priority.BackgroundPriority;

        if (win) {
            switch (getNativeProcessPriority()) {
                case Kernel32.REALTIME_PRIORITY_CLASS:
                case Kernel32.HIGH_PRIORITY_CLASS:
                case Kernel32.ABOVE_NORMAL_PRIORITY_CLASS:
                case Kernel32.NORMAL_PRIORITY_CLASS:
                    return Priority.NormalPriority;
                case Kernel32.BELOW_NORMAL_PRIORITY_CLASS:
                    return Priority.LowerPriority;
                case Kernel32.IDLE_PRIORITY_CLASS:
                    return Priority.IdlePriority;
            }
        } else if (lin) {
            int prio = getNativeProcessPriority();
            if (prio <= 0)
                return Priority.NormalPriority;
            else if (prio < 19)
                return Priority.LowerPriority;
            else
                return Priority.IdlePriority;
        }
    }
    
    public static boolean set(Priority prio) {
        if (!init())
            return false;

        if (prio == Priority.BackgroundPriority) {
            enterBackgroundMode();
        } else {
            exitBackgroundMode();
            if (win) {
                switch (prio) {
                    case Priority.NormalPriority:
                        setNativeProcessPriority(Kernel32.NORMAL_PRIORITY_CLASS);
                    case Priority.LowerPriority:
                        setNativeProcessPriority(Kernel32.BELOW_NORMAL_PRIORITY_CLASS);
                    case Priority.IdlePriority:
                        setNativeProcessPriority(Kernel32.IDLE_PRIORITY_CLASS);
                }
            } else if (lin) {
                switch (prio) {
                    case Priority.NormalPriority:
                        setNativeProcessPriority(0);
                    case Priority.LowerPriority:
                        setNativeProcessPriority(5);
                    case Priority.IdlePriority:
                        setNativeProcessPriority(19);
                }
            }
        }
        return getProcessPriority() == prio;
    }
}

