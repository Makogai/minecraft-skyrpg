---
title: Coins and the bank
category: economy
order: 1
icon: minecraft:gold_ingot
summary: Your purse and your bank: how coins are kept, the daily interest, and how to pay another player.
---
Coins are the currency of SkyRPG. You carry them in your **purse**, you can keep them in the **bank** at Skyhaven, and you can send them to other players. Everything that costs coins, from a pickaxe to an enchant to a slayer fee, comes out of your purse. This page explains how to move coins around, how the bank's daily interest works, and how to pay a friend.

## The purse

Your purse is the coins you carry. It is saved with your character and **kept when you die**.

- A small pill in the top left of your screen shows the purse. You can move or hide it in the HUD editor. See [HUD and minimap](hud-and-minimap).
- The Profile page in the K menu has two tiles, **Purse** and **Bank**.
- `/coins` shows your purse and your bank in chat. `/coins <player>` shows someone else's.
- Large numbers are shortened to fit, for example `1.23B`. Hover to see the full number.

Coins come from quests, contracts, events, selling things, and monster loot. See [Selling and shops](selling-and-shops), [Contracts](contracts), [Events](events) and [Quests and the Journal](quests-and-journal).

## The bank

Cassius Vellum keeps the **Skyhaven Bank**, at the north end of the town. Right-click him to open the bank screen. You must be at the banker to use it, because the server checks that you are still talking to him.

### Depositing and withdrawing

The screen has a card for each direction.

| Button | What it does |
| --- | --- |
| **All** (deposit) | Moves your whole purse into the bank |
| **Half** (deposit) | Moves half your purse into the bank |
| **All** (withdraw) | Moves your whole bank balance into the purse |
| **Half** (withdraw) | Moves half the bank balance into the purse |
| **Deposit** and **Withdraw** with a typed amount | Move exactly that many coins |

You can type an amount in the box. Letters work as shorthand: `2.5k` is 2,500, `1m` is 1,000,000 and `1b` is 1,000,000,000. Press **Enter** to deposit, or **Shift+Enter** to withdraw.

The screen also shows your last **ten** deposits and withdrawals with their times. Interest payments appear in that list as deposits.

### Why use the bank?

Nothing takes coins from you when you die, so the bank is not a safe. Both the purse and the bank are kept. The reason to use the bank is the **interest**.

### Interest

Your first login each day pays interest into the bank.

| Rule | Value |
| --- | --- |
| Rate | 1% of your bank balance |
| Daily cap | 5,000 coins |
| When | The first time you log in on a new calendar day |
| Where it goes | Into the bank, not the purse |

The 1% hits the cap at a balance of 500,000 coins. Keeping more than that earns the same 5,000. You need at least 100 coins in the bank to earn a whole coin. Your very first login, with an empty bank, pays nothing. You get a chat line when interest arrives: "Your bank paid N in interest today (1%, up to 5,000)."

Interest is paid only when you log in, so missing a day costs you that day's interest. It does not stack up.

> [!TIP]
> Put coins you are saving for something expensive into the bank at the end of each session. A balance of 100,000 earns 1,000 a day.

## Paying other players

There are two ways to send coins to another player. Both do the same checks.

### The Pay page

Press **K** and open **Pay**.

1. Pick someone from the list of online players. Narrow screens show the list first, then the payment panel with a back arrow.
2. Type an amount, or tap a preset: **100**, **1k**, **10k**, **Half** or **All**. `2.5k` and `1m` work.
3. Press **Enter**, or click Send.

The page shows your purse counting down as the coins leave, and says "Sent N to Name" when it is done.

### The command

```
/pay <player> <amount>
```

The player must be online.

### Rules

- You cannot pay yourself.
- The minimum payment is 1 coin.
- You need the coins in your **purse**. Bank coins must be withdrawn first.
- **Big payments take a second click.** On the Pay page, a payment of 10,000 coins or more, or of half your purse or more, asks you to click again within 4 seconds to confirm. The page says how much of your purse it is.
- The receiver gets a "paid you" message and a chime. Each of you hears a sound.
- Payments are logged on the server.

## Useful numbers

| What | Value |
| --- | --- |
| Interest | 1% a day, up to 5,000 |
| Bank history | Last 10 entries |
| Big payment | 10,000 coins, or half your purse |
| Contract reroll | 500 coins |
| Talent reset | Free once a day, then 2,000 coins |

## See also

- [Selling and shops](selling-and-shops)
- [Contracts](contracts)
- [K menu](k-menu)
- [Controls and keys](controls-and-keys)
