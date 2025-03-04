# FrizzlenStore

A fully customizable, self-hosted donation and web store system for Minecraft servers. Unlike Buycraft (Tebex), FrizzlenStore gives full control to server owners, eliminating transaction fees and third-party dependencies.

## Features

### 🛒 Web Storefront
- Modern UI: Clean and responsive online shop for players to browse packages.
- Category System: Organize items into ranks, kits, commands, cosmetics, and more.
- Dynamic Pricing: Supports sales, discounts, coupons, and regional pricing.

### 💳 Payment Processing
- Multiple Gateways: Integrates with PayPal, Stripe, and crypto payments (Bitcoin, Ethereum).
- No Transaction Fees: Payments go directly to the server owner.
- Subscription Support: Monthly/weekly recurring purchases (e.g., VIP ranks).

### ⚡ In-Game Integration
- Automatic Purchases: Players receive their items instantly after payment.
- Offline Queueing: Ensures rewards are delivered when players log in.
- Custom Messages: Announce purchases in chat or via Discord webhooks.

### 🛠️ Admin Panel
- Web-Based Dashboard: Manage products, track sales, and generate reports.
- Order Logs: View all transactions, refund requests, and purchase history.
- User Management: Ban abusers from making purchases.

### 📊 Analytics & Logs
- Sales Reports: Track revenue, best-selling items, and customer trends.
- Order History: Logs for disputes and chargeback protection.
- Live Statistics: Active purchases, store traffic, and player engagement.

### 🔗 Third-Party Integrations
- Discord Webhooks: Notify Discord when a purchase is made.
- Custom APIs: Allow other plugins to check player ranks and store data.
- Bukkit & Proxy Support: Works on Spigot, Paper, and BungeeCord/Velocity networks.

## Installation

### Requirements
- Java 21 or higher
- Minecraft server running Paper 1.21+
- MySQL or SQLite database
- Web server for hosting the frontend (optional)

### Plugin Installation
1. Download the latest release from the [Releases](https://github.com/frizzlenpop/frizzlenstore/releases) page.
2. Place the JAR file in your server's `plugins` folder.
3. Start or restart your server.
4. Configure the plugin by editing the files in the `plugins/FrizzlenStore` directory.

### Web Frontend Installation
1. Clone the web frontend repository: `git clone https://github.com/frizzlenpop/frizzlenstore-web.git`
2. Install dependencies: `npm install`
3. Configure the `.env` file with your server's API URL and token.
4. Build the frontend: `npm run build`
5. Deploy the built files to your web server.

## Configuration

### Main Configuration
Edit `plugins/FrizzlenStore/config.yml` to configure the plugin's general settings.

### Database Configuration
Edit `plugins/FrizzlenStore/database.yml` to configure the database connection.

### Payment Gateways
Edit `plugins/FrizzlenStore/payment-gateways.yml` to configure payment gateways.

### Messages
Edit `plugins/FrizzlenStore/messages.yml` to customize plugin messages.

## Commands

- `/frizzlenstore help` - Show help information
- `/frizzlenstore reload` - Reload the plugin configuration
- `/frizzlenstore store` - Get the store URL

## Permissions

- `frizzlenstore.admin` - Access to administrative commands
- `frizzlenstore.use` - Access to basic commands

## API Documentation

The plugin provides a REST API for communication with the web frontend. See the [API Documentation](https://github.com/frizzlenpop/frizzlenstore/wiki/API-Documentation) for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

If you need help with the plugin, please open an issue on GitHub or join our [Discord server](https://discord.gg/frizzlenstore). 