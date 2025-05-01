package com.floye.safarizone.util;

import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyHandler {

    /**
     * Récupère le compte d'un joueur de manière asynchrone.
     *
     * @param playerId L'UUID du joueur.
     * @return Un CompletableFuture contenant le compte du joueur, ou null si le compte n'existe pas.
     */
    public static CompletableFuture<Account> getAccount(UUID playerId) {
        EconomyService economyService = EconomyService.instance();
        return economyService.account(playerId)
                .exceptionally(ex -> {
                    ex.printStackTrace(); // Log l'erreur
                    return null;
                });
    }

    /**
     * Récupère la balance du compte d'un joueur.
     *
     * @param account Le compte du joueur.
     * @return La balance du compte sous forme de double.
     */
    public static double getBalance(Account account) {
        return account.balance().doubleValue();
    }

    /**
     * Ajoute de l'argent au compte d'un joueur.
     *
     * @param account Le compte du joueur.
     * @param amount  Le montant à ajouter.
     * @return true si l'opération a réussi, false sinon.
     */
    public static boolean add(Account account, double amount) {
        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        EconomyTransaction transaction = account.deposit(bigAmount);
        return transaction.result() == EconomyResultType.SUCCESS;
    }

    /**
     * Retire de l'argent du compte d'un joueur.
     *
     * @param account Le compte du joueur.
     * @param amount  Le montant à retirer.
     * @return true si l'opération a réussi, false sinon.
     */
    public static boolean remove(Account account, double amount) {
        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        EconomyTransaction transaction = account.withdraw(bigAmount);
        return transaction.result() == EconomyResultType.SUCCESS;
    }
}