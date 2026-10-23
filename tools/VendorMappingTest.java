import java.util.*;

/**
 * The port-number mapping, checked against the input ids upstream recorded for three Xiaomi sets
 * (Xiaomi MiTV-ANSP0, MTEQ0 and MFTR0 — see docs/devices/ in the upstream repository).
 *
 * <p>This is a copy of the logic in VendorInputs.sourceNumbers, kept runnable on its own so a
 * change to the mapping can be checked without a device:
 *
 * <pre>javac tools/VendorMappingTest.java &amp;&amp; java VendorMappingTest</pre>
 */
public class VendorMappingTest {
    static final int SOURCE_HDMI_1 = 23, SOURCE_AV = 2;
    static int pass = 0, fail = 0;

    static String hardwareId(String id){ int s=id.lastIndexOf('/'); return s<0?id:id.substring(s+1); }
    static int hwNum(String id){
        String hw=hardwareId(id); int i=hw.length();
        while(i>0 && Character.isDigit(hw.charAt(i-1))) i--;
        try { return Integer.parseInt(hw.substring(i)); } catch(Exception e){ return Integer.MAX_VALUE; }
    }
    static String serviceName(String id){
        int s=id.lastIndexOf('/'); String head=s<0?id:id.substring(0,s);
        int d=head.lastIndexOf('.'); return d<0?head:head.substring(d+1);
    }
    static boolean isComposite(String id, String type){
        if (type.equals("composite")) return true;
        if (type.equals("tuner")) return false;
        String cls=serviceName(id).toLowerCase(Locale.ROOT);
        return cls.contains("composite") || cls.contains("av");
    }
    static Map<String,Integer> numbers(List<String[]> inputs){
        Map<String,Integer> out=new LinkedHashMap<>();
        List<String[]> hdmi=new ArrayList<>();
        for(String[] in:inputs) if(in[1].equals("hdmi")) hdmi.add(in);
        hdmi.sort(new Comparator<String[]>(){ public int compare(String[] a, String[] b){
            return hwNum(a[0]) - hwNum(b[0]); }});
        for(int i=0;i<hdmi.size();i++){ if(i>2)break; out.put(hdmi.get(i)[0], SOURCE_HDMI_1+i); }
        for(String[] in:inputs){ if(in[1].equals("hdmi"))continue;
            if(isComposite(in[0], in[1])) out.put(in[0], SOURCE_AV); }
        return out;
    }
    static void check(String label, Object got, Object want){
        if(String.valueOf(got).equals(String.valueOf(want))){ pass++; System.out.println("  PASS  "+label+" = "+got); }
        else { fail++; System.out.println("  FAIL  "+label+" = "+got+"  (want "+want+")"); }
    }
    public static void main(String[] a){
        List<String[]> ansp0 = Arrays.asList(
            new String[]{"com.droidlogic.tvinput/.services.Hdmi1InputService/HW5","hdmi"},
            new String[]{"com.droidlogic.tvinput/.services.Hdmi2InputService/HW6","hdmi"},
            new String[]{"com.droidlogic.tvinput/.services.Hdmi3InputService/HW7","hdmi"},
            new String[]{"com.droidlogic.tvinput/.services.AV1InputService/HW1","composite"},
            new String[]{"com.droidlogic.tvinput/.services.ADTVInputService/HW16","tuner"});
        Map<String,Integer> m = numbers(ansp0);
        System.out.println("=== ANSP0 小米电视5 Pro (issue #5 的那台) ===");
        check("HDMI1", m.get("com.droidlogic.tvinput/.services.Hdmi1InputService/HW5"), 23);
        check("HDMI2", m.get("com.droidlogic.tvinput/.services.Hdmi2InputService/HW6"), 24);
        check("HDMI3", m.get("com.droidlogic.tvinput/.services.Hdmi3InputService/HW7"), 25);
        check("AV",    m.get("com.droidlogic.tvinput/.services.AV1InputService/HW1"), 2);
        check("tuner 不编号", m.get("com.droidlogic.tvinput/.services.ADTVInputService/HW16"), null);

        List<String[]> mteq0 = Arrays.asList(
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW5","hdmi"},
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW6","hdmi"},
            new String[]{"com.mediatek.tvinput/.composite.CompositeInputService/HW2","composite"},
            new String[]{"com.mediatek.tvinput/.tuner.TunerInputService/HW1","tuner"},
            new String[]{"com.mediatek.tvinput/.tuner.TunerInputService/HW0","tuner"});
        m = numbers(mteq0);
        System.out.println("=== MTEQ0 小米EA Pro ===");
        check("HDMI1", m.get("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"), 23);
        check("HDMI2", m.get("com.mediatek.tvinput/.hdmi.HDMIInputService/HW6"), 24);
        check("AV",    m.get("com.mediatek.tvinput/.composite.CompositeInputService/HW2"), 2);

        List<String[]> mftr0 = Arrays.asList(
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW5","hdmi"},
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW6","hdmi"},
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW7","hdmi"},
            new String[]{"com.mediatek.tvinput/.hdmi.HDMIInputService/HW8","hdmi"},
            new String[]{"com.mediatek.tvinput/.composite.CompositeInputService/HW2","composite"});
        m = numbers(mftr0);
        System.out.println("=== MFTR0 小米 ===");
        check("HDMI1", m.get("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"), 23);
        check("HDMI3", m.get("com.mediatek.tvinput/.hdmi.HDMIInputService/HW7"), 25);
        check("AV",    m.get("com.mediatek.tvinput/.composite.CompositeInputService/HW2"), 2);
        check("第4个HDMI(无物理口)不编号", m.get("com.mediatek.tvinput/.hdmi.HDMIInputService/HW8"), null);

        // 排序稳定性：HW10 必须排在 HW5 之后（字符串排序会错）
        List<String[]> hw10 = Arrays.asList(
            new String[]{"x/.a/HW10","hdmi"}, new String[]{"x/.b/HW5","hdmi"}, new String[]{"x/.c/HW2","hdmi"});
        m = numbers(hw10);
        System.out.println("=== HW 号排序（HW10 不能排在 HW5 前）===");
        check("HW2→HDMI1", m.get("x/.c/HW2"), 23);
        check("HW5→HDMI2", m.get("x/.b/HW5"), 24);
        check("HW10→HDMI3", m.get("x/.a/HW10"), 25);

        System.out.println("\n通过 "+pass+" / 失败 "+fail);
        if(fail>0) System.exit(1);
    }
}
