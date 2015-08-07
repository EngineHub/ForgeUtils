package org.enginehub.util.forge;

import bsh.EvalError;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import bsh.Interpreter;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BeanShellCommand implements ICommand {
    private List<String> aliases;

    public BeanShellCommand() {
        this.aliases = new ArrayList<String>();
        this.aliases.add("bsh");
        this.aliases.add(">");
    }

    @Override
    public String getName() {
        return "beanshell";
    }

    @Override
    public String getCommandUsage(ICommandSender icommandsender) {
        return "bsh <eval>";
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }

    @Override
    public void execute(ICommandSender icommandsender, String[] astring) {
        if (astring.length == 0) {
            icommandsender.addChatMessage(new ChatComponentText("No args given."));
            return;
        }

        Interpreter in = get(icommandsender).getInterpreter();
        try {
            String s = Joiner.on(' ').join(astring);
            icommandsender.addChatMessage(
                    new ChatComponentText("$ ").setChatStyle(GREEN)
                            .appendSibling(new ChatComponentText(s).setChatStyle(GRAY)));
            Object obj = in.eval(s);
            icommandsender.addChatMessage(
                    new ChatComponentText("> ").setChatStyle(GREEN)
                            .appendSibling(obj == null ? new ChatComponentText("null").setChatStyle(RED)
                                                       : new ChatComponentText(obj.toString())));
        } catch (EvalError e) {
            icommandsender.addChatMessage(
                    new ChatComponentText(">> ").setChatStyle(RED)
                            .appendSibling(new ChatComponentText(e.toString())));
        }

    }

    private static ChatStyle RED = new ChatStyle().setColor(EnumChatFormatting.RED);
    private static ChatStyle GREEN = new ChatStyle().setColor(EnumChatFormatting.GREEN);
    private static ChatStyle GRAY = new ChatStyle().setColor(EnumChatFormatting.GRAY);

    @Override
    public boolean canCommandSenderUse(ICommandSender icommandsender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] astring, int i) {
        return false;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }

    private Map<ICommandSender, InterpreterSession> sessions = Maps.newHashMap();
    public InterpreterSession get(ICommandSender key) {
        InterpreterSession session = sessions.get(key);
        if (session == null) {
            session = new InterpreterSession();
            sessions.put(key, session);
            session.setInterpreter(new Interpreter());
            try {
                session.getInterpreter().set("me", key);
            } catch (EvalError e) {
                key.addChatMessage(new ChatComponentText("couldn't set 'me' var in interpreter:\n" + e));
            }
        }
        return session;
    }

    public static class InterpreterSession {
        private Interpreter interpreter;

        public Interpreter getInterpreter() {
            return interpreter;
        }
        public void setInterpreter(Interpreter interpreter) {
            this.interpreter = interpreter;
        }
    }
}
