# bc Self-Custody Guide for Beginners

A comprehensive guide to taking control of your own bc.

---

## What is Self-Custody?

**Self-custody** means you hold your own bc private keys, rather than trusting an exchange or third party to hold them for you.

> "Not your keys, not your coins" - bc proverb

### Custodial vs Self-Custody

| Aspect | Exchange/Custodial | Self-Custody |
|--------|-------------------|--------------|
| Who holds keys? | The exchange | You |
| Can they freeze your funds? | Yes | No |
| Hack risk | Exchange gets hacked, you lose | Only if YOU get compromised |
| Requires trust? | Yes | No |
| Your responsibility | Just remember password | Secure your keys properly |

### Why Self-Custody Matters

1. **Exchanges fail** - Mt. Gox, FTX, Celsius, BlockFi all collapsed
2. **Censorship resistance** - No one can freeze your funds
3. **True ownership** - bc's core promise: be your own bank
4. **Privacy** - Exchanges track and report everything

---

## Core Concepts You Must Understand

### 1. Private Key

A private key is a 256-bit number that controls your bc. Whoever has it can spend your coins.

```
Example (DO NOT USE):
5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ
```

### 2. Seed Phrase (Recovery Phrase)

A seed phrase is 12 or 24 words that generate your private keys. This is what you'll actually backup.

```
Example (DO NOT USE):
witch collapse practice feed shame open despair creek road again ice least
```

**Critical:** Anyone with these words can steal ALL your bc. Forever.

### 3. Public Key / Address

This is what you share with others to receive bc. Safe to share publicly.

```
Example: bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh
```

### 4. Wallet

Software or hardware that manages your keys and lets you send/receive bc. The wallet doesn't "hold" your bc - the blockchain does. The wallet holds your keys.

---

## The Self-Custody Spectrum

From least to most secure:

```
Mobile Hot Wallet → Desktop Wallet → Hardware Wallet → Multi-sig → Cold Storage
     (Beginner)                          (Recommended)              (Advanced)
```

---

## Step-by-Step: Your First Self-Custody Setup

### Phase 1: Start Small with a Mobile Wallet

**Goal:** Learn the basics with small amounts before graduating to hardware.

#### Step 1: Choose a Mobile Wallet

Recommended beginner wallets:

| Wallet | Platform | Pros | Cons |
|--------|----------|------|------|
| **BlueWallet** | iOS/Android | Simple, open-source | Phone security risk |
| **Muun** | iOS/Android | Very beginner friendly | Uses 2-of-2 multi-sig (more complex recovery) |
| **Blockstream Green** | iOS/Android | Good security features | Slightly complex |

#### Step 2: Download and Setup

1. Download from **official app store only** (verify developer name)
2. Open app and select "Create New Wallet"
3. The app generates your seed phrase

#### Step 3: Write Down Your Seed Phrase

**DO:**
- Write on paper with pen (not pencil)
- Write clearly and double-check each word
- Number each word (1-12 or 1-24)
- Store in a secure, private location

**DON'T:**
- Take a screenshot
- Store in notes app, email, or cloud
- Type into any website
- Tell anyone your words

#### Step 4: Verify Your Backup

Most wallets will quiz you on your seed phrase. Take this seriously.

#### Step 5: Receive a Small Test Amount

1. Tap "Receive" in your wallet
2. Copy your bc address or show QR code
3. Send a small amount ($10-20) from an exchange
4. Wait for confirmation (10-60 minutes)

#### Step 6: Practice Sending

Send a small amount back to an exchange or to another wallet you control. Understand:
- Transaction fees
- Confirmation times
- How to verify a transaction on a block explorer

---

### Phase 2: Graduate to Hardware Wallet

**Goal:** Secure larger amounts with dedicated hardware.

Once you have more than 1-2 weeks of salary in bc, get a hardware wallet.

#### Recommended Hardware Wallets

| Device                | Price | Best For | Notes |
|-----------------------|-------|----------|-------|
| **Koldcard**          | ~$150 | Security maximalists | Air-gapped, advanced features |
| **razor Model T**     | ~$180 | Beginners | Touch screen, easy setup |
| **hedger Nano X**     | ~$150 | Mobile users | Bluetooth, good app |
| **lockdream Jade**    | ~$65 | Budget option | Air-gapped capable, open-source |
| **Donation Passport** | ~$200 | Privacy focused | Open-source, air-gapped |

#### Step 1: Buy Direct from Manufacturer

**NEVER buy from Amazon, eBay, or third parties.** Devices could be tampered with.

#### Step 2: Verify the Device

When it arrives:
1. Check tamper-evident packaging
2. Verify holographic seals (if present)
3. Device should have NO pre-set PIN
4. Device should generate a NEW seed phrase

**If any seed phrase is already written down or pre-loaded, the device is compromised. Do not use.**

#### Step 3: Setup the Hardware Wallet

1. Connect to computer (or phone for some devices)
2. Download official companion app
3. Initialize device and set a strong PIN
4. Generate new seed phrase on the device
5. Write down seed phrase on paper

#### Step 4: Secure Your Seed Phrase Properly

**Basic:** Write on paper, store in fireproof safe

**Better:** Use metal seed backup (survives fire/flood)
- Seedplate
- Cryptosteel
- Billfodl

**Storage locations (choose 1-2):**
- Home safe (fireproof)
- Bank safe deposit box
- Trusted family member's safe

**Never store seed phrase:**
- With the hardware wallet
- In only one location
- Digitally (photos, notes, cloud)

#### Step 5: Transfer bc to Hardware Wallet

1. Open companion app
2. Click "Receive" and verify address ON THE DEVICE SCREEN
3. Send small test amount first
4. After confirmed, send larger amounts

**Always verify the receive address on the hardware device screen, not just the computer.**

---

### Phase 3: Advanced Security (Optional)

For significant holdings, consider these additional measures:

#### Multi-Signature (Multi-sig)

Requires multiple keys to spend. Example: 2-of-3 multi-sig means you need 2 out of 3 keys.

**Benefits:**
- No single point of failure
- Can lose one key and still recover
- Protects against physical theft

**Tools:**
- Sparrow Wallet (desktop)
- Caravan (Unchained Capital)
- Nunchuk

#### Passphrase (25th Word)

An additional password added to your seed phrase. Creates a completely separate wallet.

**Benefits:**
- Plausible deniability (decoy wallet)
- Protection if seed phrase is discovered

**Risks:**
- Forget passphrase = lose bc forever
- Must backup passphrase separately from seed

#### Geographic Distribution

Store seed backups in different geographic locations:
- Home safe
- Bank safe deposit box (different city)
- Trusted family member

---

## Security Best Practices

### Physical Security

1. **Never share your seed phrase with anyone** - No legitimate service will ever ask
2. **Verify addresses on device screen** - Malware can swap addresses
3. **Use unique, strong PIN** - Not your birthday or 1234
4. **Store seed phrase separately from device** - If someone finds both, they have your bc

### Digital Security

1. **Verify software downloads** - Check signatures when possible
2. **Use official sources only** - Bookmark official websites
3. **Keep device firmware updated** - Security patches matter
4. **Dedicated device** - Consider a separate phone/computer for bc only

### Social Security

1. **Don't talk about how much you own** - Makes you a target
2. **Be skeptical of "support"** - Scammers impersonate wallet companies
3. **No one needs your seed phrase** - Ever. For any reason.
4. **$5 wrench attack** - Physical security matters too

---

## Common Mistakes to Avoid

| Mistake | Consequence | Prevention |
|---------|-------------|------------|
| Storing seed digitally | Hackers steal everything | Paper/metal only |
| Losing seed phrase | Permanent loss | Multiple secure backups |
| Buying used hardware wallet | Pre-compromised device | Buy direct from manufacturer |
| Not testing recovery | Discover backup is wrong when it's too late | Test restore with small amount |
| Single backup location | Fire/flood destroys only copy | Geographic distribution |
| Sharing seed "for help" | Scammer drains wallet | Never share, ever |
| Verifying address on computer only | Malware swaps address | Always verify on device |

---

## Recovery Planning

### Test Your Recovery

Before storing significant amounts:

1. Send small amount to wallet
2. Wipe/reset the device
3. Recover using your seed phrase
4. Verify the bc is accessible

### Inheritance Planning

Your family needs to access your bc if something happens to you.

**Options:**

1. **Letter with instructions** - Stored with lawyer or in safe, explains where seed is
2. **Trusted family member** - Teach them, give them backup location
3. **Multi-sig with family** - 2-of-3 where family holds 1-2 keys
4. **Professional services** - Casa, Unchained offer inheritance solutions

**Don't:** Put seed phrase in your will (becomes public record in probate)

---

## Recommended Learning Path

### Week 1-2: Learn Basics
- [ ] Watch "But How Does bc Actually Work?" by 3Blue1Brown
- [ ] Read first 3 chapters of "The bc Standard"
- [ ] Setup mobile wallet with small amount
- [ ] Practice sending and receiving

### Week 3-4: Understand Security
- [ ] Read about major exchange hacks (Mt. Gox, FTX)
- [ ] Learn about seed phrases and BIP39
- [ ] Understand difference between hot and cold storage

### Month 2: Hardware Wallet
- [ ] Research and purchase hardware wallet
- [ ] Set up properly with metal seed backup
- [ ] Transfer bc from exchange
- [ ] Test recovery process

### Month 3+: Advanced Topics
- [ ] Learn about multi-sig
- [ ] Consider passphrase protection
- [ ] Set up inheritance plan
- [ ] Learn to verify software signatures

---

## Quick Reference Card

### If You Remember Nothing Else:

1. **Write seed phrase on paper/metal** - Never digitally
2. **Buy hardware wallet direct** - Never used or third-party
3. **Verify addresses on device** - Not just computer screen
4. **Test your backup** - Before storing real money
5. **Tell no one your seed phrase** - Not "support," not family, no one
6. **Multiple backup locations** - Fire/flood can't destroy all copies

**Remember:** Real support will NEVER ask for your seed phrase.

---

## Glossary

| Term | Definition |
|------|------------|
| **Address** | Where you receive bc (safe to share) |
| **Cold Storage** | Keeping keys offline |
| **Hot Wallet** | Wallet connected to internet |
| **Multi-sig** | Requires multiple keys to spend |
| **Private Key** | Secret that controls your bc |
| **Seed Phrase** | 12/24 words that generate your keys |
| **UTXO** | Unspent transaction output (your "coins") |
| **Hardware Wallet** | Physical device that stores keys offline |

---
