package net.threadly.commandexpress;

import net.threadly.commandexpress.args.CommandContext;
import net.threadly.commandexpress.args.CommandElement;
import net.threadly.commandexpress.exception.CastNotPossibleException;
import net.threadly.commandexpress.result.CommandResult;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class CommandExpress extends JavaPlugin {

    public static void registerCommandEntryPoint(JavaPlugin plugin, final CommandSpec rootCmd, String alias){
        Objects.requireNonNull(plugin.getCommand(alias)).setExecutor((sender, command, label, args) -> {

            AtomicReference<CommandSpec> currentSpec = new AtomicReference<>();

            ListIterator<String> commandPathIterator = Arrays.asList(args).listIterator();
            ArrayList<CommandElement> commandSpectedArguments = new ArrayList<>();

            Map<String, Object> arguments = new HashMap<>();

            if(commandPathIterator.hasNext()){
                StringBuilder pathBuilder = new StringBuilder();
                AtomicReference<String> currentArg = new AtomicReference<>();
                currentSpec.set(rootCmd);

                while(commandPathIterator.hasNext()) {
                    currentArg.set(commandPathIterator.next());
                    pathBuilder.append(currentArg.get()).append(" ");
                    if(currentSpec.get().getChilds().isPresent()) {
                        currentSpec.get().getChilds().get().forEach((child) -> {
                            if (child.getAliases().contains(currentArg.get())) currentSpec.set(child);
                        });
                    }else{
                        break;
                    }
                }

                commandPathIterator.previous();

                currentSpec.get().getArguments().ifPresent((commandElements) ->
                    commandSpectedArguments.addAll(Arrays.asList(commandElements)));

                List<String> passedArguments = new ArrayList<>();
                commandPathIterator.forEachRemaining(passedArguments::add);

                if(passedArguments.size() < commandSpectedArguments.size()) {
                    sender.sendMessage(getCorrectUsageText(currentSpec.get(), pathBuilder.toString()));
                    return true;
                }

                try {
                    arguments = populateArguments(passedArguments, commandSpectedArguments);
                } catch (CastNotPossibleException e) {
                    sender.sendMessage(getCorrectUsageText(currentSpec.get(), pathBuilder.toString()));
                    return true;
                }
            } else {
                currentSpec.set(rootCmd);
            }

            CommandContext context = new CommandContext(arguments, sender, System.currentTimeMillis()/1000);

            if(currentSpec.get().isPlayerOnly() && !(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Command for player only.");
                return true;
            }

            if(currentSpec.get().getPermission().isPresent() && !sender.hasPermission(currentSpec.get().getPermission().get())) {
                sender.sendMessage(ChatColor.RED + "You are not allowed to perform this command.");
                return true;
            }

            CommandResult cmdResult = currentSpec.get().getExecutor().execute(context, args);

            cmdResult.getResult().ifPresent(result -> {
                cmdResult.getMessage().ifPresent(message -> {
                    switch(result){
                        case NONE:
                            sender.sendMessage(message);
                            break;
                        case FAILURE:
                            sender.sendMessage(ChatColor.RED + message);
                            if(sender instanceof Player){
                                Player p = (Player) sender;
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                            }
                            break;
                        case SUCCESS:
                            sender.sendMessage(ChatColor.GREEN + message);
                            if(sender instanceof Player){
                                Player p = (Player) sender;
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                            }
                            break;
                    }
                });
            });

            return false;
        });
    }

    private static String getCorrectUsageText(CommandSpec spec, String path) {
        if (spec.getArguments().isPresent()) {
            StringBuilder correctUsage = new StringBuilder();
            correctUsage.append(ChatColor.RED + "Correct usage: ").append(path).append(" ");
            Arrays.asList(spec.getArguments().get())
                    .forEach(arg -> correctUsage.append(ChatColor.RED + "<").append(arg.getKey()).append(ChatColor.RED + ">").append(" "));
            return correctUsage.toString();
        }
        return ChatColor.RED + "Correct usage: " + path;
    }

    private static Map<String, Object> populateArguments(List<String> passedArgs, List<CommandElement> spectedArguments) throws CastNotPossibleException {
        Map<String, Object> args = new HashMap<>();
        Iterator<String> passedArgsIterator = passedArgs.iterator();
        Iterator<CommandElement> spectedArgumentsIterator = spectedArguments.iterator();

        while (passedArgsIterator.hasNext()) {
            String passedArg = passedArgsIterator.next();
            if(spectedArgumentsIterator.hasNext()) {
                CommandElement spectedArgument = spectedArgumentsIterator.next();
                if(spectedArgument.isJoinString()) {
                    StringBuilder passedText = new StringBuilder();
                    passedText.append(passedArg).append(" ");
                    passedArgsIterator.forEachRemaining(x -> passedText.append(x).append(" "));
                    args.put(spectedArgument.getKey(), passedText.toString().trim());
                }else{
                    args.put(spectedArgument.getKey(), spectedArgument.cast(passedArg));
                }
            }
        }

        return args;
    }
}

