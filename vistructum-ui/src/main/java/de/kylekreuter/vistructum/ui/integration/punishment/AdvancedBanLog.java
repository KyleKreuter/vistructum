package de.kylekreuter.vistructum.ui.integration.punishment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

final class AdvancedBanLog implements PunishmentLog {

    private static final Map<String, PunishmentKind> KINDS = Map.ofEntries(
            Map.entry("BAN", PunishmentKind.BAN), Map.entry("TEMP_BAN", PunishmentKind.BAN),
            Map.entry("IP_BAN", PunishmentKind.BAN), Map.entry("TEMP_IP_BAN", PunishmentKind.BAN),
            Map.entry("MUTE", PunishmentKind.MUTE), Map.entry("TEMP_MUTE", PunishmentKind.MUTE),
            Map.entry("WARNING", PunishmentKind.WARN), Map.entry("TEMP_WARNING", PunishmentKind.WARN),
            Map.entry("KICK", PunishmentKind.KICK));

    private final Object manager;
    private final Method punishments;
    private final Method id;
    private final Method type;
    private final Method reason;
    private final Method operator;
    private final Method start;
    private final Method end;
    private final Executor queries;

    private AdvancedBanLog(ClassLoader loader, Executor queries) throws ReflectiveOperationException {
        Class<?> managerType = Class.forName("me.leoko.advancedban.manager.PunishmentManager", true, loader);
        Class<?> punishmentType = Class.forName("me.leoko.advancedban.utils.Punishment", true, loader);
        Class<?> kindType = Class.forName("me.leoko.advancedban.utils.PunishmentType", true, loader);
        this.manager = managerType.getMethod("get").invoke(null);
        this.punishments = managerType.getMethod("getPunishments", String.class, kindType, boolean.class);
        this.id = punishmentType.getMethod("getId");
        this.type = punishmentType.getMethod("getType");
        this.reason = punishmentType.getMethod("getReason");
        this.operator = punishmentType.getMethod("getOperator");
        this.start = punishmentType.getMethod("getStart");
        this.end = punishmentType.getMethod("getEnd");
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    static PunishmentLog connect(ClassLoader loader, Executor queries) throws ReflectiveOperationException {
        return new AdvancedBanLog(loader, queries);
    }

    @Override
    public String source() {
        return "AdvancedBan";
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID player) {
        String target = player.toString().replace("-", "");
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<Object> active = select(target, true).stream().map(this::idOf).collect(Collectors.toSet());
                List<Punishment> history = new ArrayList<>();
                for (Object punishment : select(target, false)) {
                    Optional<PunishmentKind> kind = kindOf(punishment);
                    if (kind.isPresent()) {
                        history.add(punishment(punishment, kind.get(), active.contains(idOf(punishment))));
                    }
                }
                return history;
            } catch (ReflectiveOperationException e) {
                throw new CompletionException(e);
            }
        }, queries);
    }

    private List<?> select(String target, boolean current) throws ReflectiveOperationException {
        return (List<?>) invoke(punishments, manager, target, null, current);
    }

    private Optional<PunishmentKind> kindOf(Object punishment) throws ReflectiveOperationException {
        return Optional.ofNullable(KINDS.get(((Enum<?>) invoke(type, punishment)).name()));
    }

    private Object idOf(Object punishment) {
        try {
            return invoke(id, punishment);
        } catch (ReflectiveOperationException e) {
            throw new CompletionException(e);
        }
    }

    private Punishment punishment(Object punishment, PunishmentKind kind, boolean active)
            throws ReflectiveOperationException {
        long ends = (long) invoke(end, punishment);
        Optional<Instant> expiresAt = kind == PunishmentKind.KICK || ends <= 0 ? Optional.empty()
                : Optional.of(Instant.ofEpochMilli(ends));
        return new Punishment(kind, Optional.ofNullable((String) invoke(reason, punishment)),
                Optional.ofNullable((String) invoke(operator, punishment)), Optional.empty(),
                Optional.of(Instant.ofEpochMilli((long) invoke(start, punishment))), expiresAt,
                active && kind != PunishmentKind.KICK);
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
