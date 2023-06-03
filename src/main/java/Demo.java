import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Demo {
    public static void main(String[] args) {


//        String input = "订单中心 下单流程 1-1";
        String input = "订单中心|下单流程-测试 2-1";
//        String input = "TestGroup";

        String[] s = input.split(" ");
        ArrayList<String> strings = new ArrayList<>(Arrays.asList(s));
        strings.remove(strings.size() - 1);
        String threadName = String.join("", strings);

        if (threadName.contains("|")) {
            String[] split = threadName.split("\\|");
            System.out.println(split[0].trim());
            System.out.println(split[1].trim());
        }


    }
}
