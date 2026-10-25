package com.yaseen.aicore.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import java.util.ArrayDeque;
import java.util.Deque;

/** Small persistent cause->decision->outcome journal for real actions. */
public final class DecisionJournal {
    private final Deque<String> entries=new ArrayDeque<>();
    private long sequence;
    public void record(String cause,String decision,String outcome){sequence++;entries.addLast("cause="+cause+" decision="+decision+" outcome="+outcome);while(entries.size()>96)entries.removeFirst();}
    public long sequence(){return sequence;}
    public boolean hasSuccessAfter(long baseline){return sequence>baseline && entries.stream().anyMatch(s -> s.contains("outcome=SUCCESS") || s.contains("outcome=RESULT"));}
    public boolean hasRecentSuccess(){
        return entries.stream().skip(Math.max(0,entries.size()-12)).anyMatch(s -> s.contains("outcome=SUCCESS") || s.contains("outcome=RESULT"));
    }
    public String summary(){return String.join(" | ",entries.stream().skip(Math.max(0,entries.size()-20)).toList());}
    public void save(CompoundTag tag){ListTag l=new ListTag();for(String s:entries)l.add(StringTag.valueOf(s));tag.put("AICoreDecisions",l); tag.putLong("AICoreDecisionSequence",sequence);}
    public void load(CompoundTag tag){entries.clear();sequence=tag.getLong("AICoreDecisionSequence");if(!tag.contains("AICoreDecisions",9))return;ListTag l=tag.getList("AICoreDecisions",8);for(int i=0;i<l.size();i++)entries.addLast(l.getString(i));}
}
