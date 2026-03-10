# MeshRide UI Specification

## In-Ride Data Fields (RemoteViews)

### Mesh Node Count Field
```
┌──────────────────┐
│  📡  3           │  ← Green when connected, grey when disconnected
│                  │     Orange when only 1 node
└──────────────────┘
```
- Compact numeric field showing mesh icon + node count
- Color-coded: green (connected), orange (1 node only), grey (disconnected)
- Updates every 2 seconds

### Mesh Last Message Field
```
┌──────────────────────────────┐
│ [A] Alice: Regroup!    14:32│
└──────────────────────────────┘
```
- Sender initial avatar circle + truncated message + timestamp
- Max 1 line for message, ellipsis truncation
- Updates on each incoming message

## In-Ride Overlay (InRideAlert)

### Incoming Message Alert
```
┌─────────────────────────────────────┐
│ 📡  Alice                          │  ← Bold sender name, 16sp
│     Regroup at next junction        │  ← Message text, 14sp, max 2 lines
└─────────────────────────────────────┘
```
- Dark semi-transparent background (#E6000000)
- Auto-dismiss after 8 seconds
- Dispatched via karoo-ext InRideAlert API
- Max 6 words visible at a glance

## Setup Screens (Jetpack Compose)

### Status Screen (Home)
```
┌─────────────────────────────────────────────┐
│ MeshRide Setup                              │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ ● Connected                             │ │
│ │ Paired Device: Meshtastic_ABCD          │ │
│ │ Nodes on mesh: 3                        │ │
│ │ [Disconnect]                            │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Quick Messages              [Edit]      │ │
│ │ Regroup!                    [Send]      │ │
│ │ Flat tire                   [Send]      │ │
│ │ Stopping ahead              [Send]      │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Last message                            │ │
│ │ [14:32] AL: Regroup at next junction    │ │
│ │ [14:28] BO: All clear ahead             │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Nodes on mesh (3)                       │ │
│ │ Alice                           14:32   │ │
│ │ Bob                             14:28   │ │
│ │ Carol                           14:15   │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### Device Pairing Screen
```
┌─────────────────────────────────────────────┐
│ Scan for Devices                     Back   │
│                                             │
│ ⟳ Scanning...                [Stop]        │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Meshtastic_ABCD            -55 dBm      │ │
│ │ AA:BB:CC:DD:EE:01                       │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ Meshtastic_EFGH            -72 dBm      │ │
│ │ AA:BB:CC:DD:EE:02                       │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### Quick Messages Configuration Screen
```
┌─────────────────────────────────────────────┐
│ Quick Messages                       Back   │
│                                             │
│ Configure quick messages for side button    │
│ actions during rides.                       │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Button 1 (Msg 1)                       │ │
│ │ ┌─────────────────────────────────────┐ │ │
│ │ │ Regroup!                            │ │ │
│ │ └─────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ Button 2 (Msg 2)                       │ │
│ │ ┌─────────────────────────────────────┐ │ │
│ │ │ Flat tire                           │ │ │
│ │ └─────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ Button 3 (Msg 3)                       │ │
│ │ ┌─────────────────────────────────────┐ │ │
│ │ │ Stopping ahead                      │ │ │
│ │ └─────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

## Design Constraints

- **Karoo screen**: Landscape ~480x272px effective ride area
- **High contrast**: White text on dark backgrounds
- **Glanceable**: Max 6 words visible in overlay at speed
- **Physical buttons**: Large touch targets, no fine-touch during rides
- **RemoteViews**: No custom view classes, PendingIntents only for clicks
- **Navigation**: Linear scrollable screens only (no bottom sheets, side drawers)
- **Font sizes**: 16sp sender name, 14sp message, 12sp secondary info
