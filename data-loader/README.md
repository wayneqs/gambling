design
======
The data loader application grabs race results and cards from a market data source and stores them in a mongo database.

The application tracks which days have been fully and partially captured.

The application get data by scraping html pages from racing web sites (sporting life and the racing post). The web sites are prone to connection and other types of technical transient failure, so the application includes a strategy for throttling queries. 