---
title: Party
category: interface
order: 5
icon: minecraft:player_head
summary: Parties of up to 8 players: they are saved, show each other's health on an overlay, share a chat tab and cannot hurt each other.
---
A party is a group of up to 8 players who play together. A party is **saved**: log out, come back tomorrow, and you are still in it. Party members see each other's health in a small overlay, talk in their own chat tab, can set a waypoint to each other and cannot hurt each other.

## Making a party

1. Type `/party invite <player>`, or open the **Party** page in the K menu, type their name and press **Invite**.
2. They get a line in chat with clickable **[Accept]** and **[Deny]**. An invite lasts **60 seconds**.
3. When they accept, the party exists. You are its **leader**.

Anyone in the party can invite more people, up to 8 in all. You can only be in one party at a time. Joins, leaves, kicks, promotions and members logging in and out are announced to the whole party.

## Commands

`/p` works the same as `/party`.

| Command | What it does |
| --- | --- |
| `/party invite <player>` | Invite a player (60 seconds to answer) |
| `/party accept [player]` | Accept an invite. Say the inviter's name if you have several |
| `/party deny [player]` | Decline an invite |
| `/party leave` | Leave your party |
| `/party kick <player>` | Remove a member (leader only) |
| `/party promote <player>` | Make a member the leader (leader only) |
| `/party disband` | Break the party up for everyone (leader only) |
| `/party list` or `/party` | Show the members and who is online |
| `/party chat <message>` | Send one message to the party |
| `/party chat` | Switch your chat to party chat, and back |
| `/pc <message>` | A quick party message |

## Staying in the party

The party stays together while members are offline. An offline member keeps their seat and shows as **Offline** on the overlay, with the place they logged out. If the leader leaves, the member who has been in the party longest becomes the leader. If only one member would be left, the party disbands.

## Party chat and the Party tab

Messages to the party and party notices (joins, leaves, invites) appear in chat with a green `PARTY` tag and a shield icon. They are in **All** and in the **Party** tab of the chat window, between Whispers and SkyRPG. The tab gets a dot when a party line arrives while you are on another tab. See [Chat](chat).

Run `/party chat` once and everything you type goes to the party until you run it again. It also switches off when you leave the party or log out.

## The party overlay

A small box on the left of the screen shows every other member, always:

- Their **face**, taken from their skin, and their **name**. The leader has a **crown**.
- A **health bar** with the numbers, in SkyRPG's units (the same as your own health bar). It is green, turns gold under half and **red and pulsing** under a quarter.
- A small tag such as **Hub**, **Nether** or **The End** when they are in another dimension than you.
- **Offline** members are greyed out, and their bar says Offline.

It is a normal HUD box: move, resize or hide it in the HUD editor. See [HUD and minimap](hud-and-minimap). It only shows while you are in a party.

## The Party page

Press **K** and open **Party**. Each member has a card with their face, crown, whether they are online, health, and where they are (dimension, coordinates and distance, or where they were last seen when offline).

- **Click a member** to set a waypoint to them. See [Waypoints](waypoints).
- The leader sees **Promote** and **Kick** on each member (Kick asks for a second click).
- **Party chat**, **Leave** and, for the leader, **Disband** are buttons at the top. Leave and Disband ask for a second click.
- **Invite a player** has a name field. Pending invites you have received show at the top with **Accept** and **Deny**.

See [K menu](k-menu).

## The tab list

Hold **Tab**. Party members have a green mark beside their face, and a **Party** panel lists everyone with their health.

## No hitting each other

Party members cannot hurt each other. The hit is cancelled, with no damage and no knockback. It covers melee, arrows and other projectiles (the shooter counts, not the arrow), thrown potions that hurt, abilities and area attacks, enchant effects, and a tamed animal hitting for its owner. You can still hurt yourself.

> [!NOTE]
> A few things are not stopped: poison or wither that keeps ticking after a thrown potion, fire, and TNT.

## See also

- [Chat](chat)
- [K menu](k-menu)
- [HUD and minimap](hud-and-minimap)
- [Combat basics](combat-basics)
