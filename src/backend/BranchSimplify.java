package backend;

import java.util.*;

/** Shrink local branch trampolines only when a conservative bound proves reachability. */
final class BranchSimplify {
    static String inverse(String operation) {
        return switch (operation) {
            case "beq" -> "bne"; case "bne" -> "beq";
            case "bltz" -> "bgez"; case "bgez" -> "bltz";
            case "blez" -> "bgtz"; case "bgtz" -> "blez";
            default -> null;
        };
    }
    static void run(StringBuilder text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.toString().split("\n")));
        Map<String,Integer> labels = new HashMap<>();
        for (int i=0;i<lines.size();i++) {
            String line=lines.get(i).trim();
            if (line.endsWith(":")) labels.put(line.substring(0,line.length()-1),i);
        }
        for (int i=0;i+4<lines.size();i++) {
            String branch=lines.get(i).trim(), jump=lines.get(i+2).trim();
            int space=branch.indexOf(' ');
            String inverted=space<0?null:inverse(branch.substring(0,space));
            if (inverted == null ||
                !lines.get(i+1).trim().equals("nop") || !jump.startsWith("j ") ||
                !lines.get(i+3).trim().equals("nop")) continue;
            String local=branch.substring(branch.lastIndexOf(',')+1).trim();
            if (!local.startsWith("__sysy_branch_") || !lines.get(i+4).trim().equals(local+":")) continue;
            String target=jump.substring(2).trim();
            Integer destination=labels.get(target);
            // Each emitted text line expands to at most eight machine instructions.
            if (destination==null || Math.abs(destination-i)*8L>32000) continue;
            lines.set(i, "    "+inverted+branch.substring(space,branch.lastIndexOf(',')+1)+" "+target);
            lines.set(i+2, ""); lines.set(i+3, ""); lines.set(i+4, "");
        }
        text.setLength(0);
        for (String line:lines) if (!line.isEmpty()) text.append(line).append('\n');
    }
}
