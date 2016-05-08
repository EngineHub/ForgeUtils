package org.enginehub.util.forge;

import bsh.EvalError;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import bsh.Interpreter;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

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
    public String getCommandName() {
        return "beanshell";
    }

    @Override
    public String getCommandUsage(ICommandSender icommandsender) {
        return "bsh <eval>";
    }

    @Override
    public List<String> getCommandAliases() {
        return this.aliases;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.addChatMessage(new TextComponentString("No args given."));
            return;
        }

        Interpreter in = get(sender).getInterpreter();
        try {
            String s = Joiner.on(' ').join(args);
            sender.addChatMessage(
                    new TextComponentString("$ ").setStyle(GREEN)
                            .appendSibling(new TextComponentString(s).setStyle(GRAY)));
            Object obj = in.eval(s);
            sender.addChatMessage(
                    new TextComponentString("> ").setStyle(GREEN)
                            .appendSibling(obj == null ? new TextComponentString("null").setStyle(RED)
                                    : new TextComponentString(obj.toString())));
        } catch (EvalError e) {
            sender.addChatMessage(
                    new TextComponentString(">> ").setStyle(RED)
                            .appendSibling(new TextComponentString(e.toString())));
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return false;
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }

    private static Style RED = new Style().setColor(TextFormatting.RED);
    private static Style GREEN = new Style().setColor(TextFormatting.GREEN);
    private static Style GRAY = new Style().setColor(TextFormatting.GRAY);

    @Override
    public boolean isUsernameIndex(String[] astring, int i) {
        return false;
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
                key.addChatMessage(new TextComponentString("couldn't set 'me' var in interpreter:\n" + e));
            }
        }
        return session;
    }

    @Override
    public int compareTo(ICommand o) {
        return 0;
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
