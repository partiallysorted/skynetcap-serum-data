package com.mmorrell.serumdata.controller;

import ch.openserum.serum.model.Market;
import com.mmorrell.serumdata.manager.MarketManager;
import com.mmorrell.serumdata.manager.MarketRankManager;
import com.mmorrell.serumdata.manager.TokenManager;
import com.mmorrell.serumdata.model.Token;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

@Controller
public class IndexController {

    private static final PublicKey DEFAULT_MARKET =
            PublicKey.valueOf("9wFFyRfZBsuAha4YcuxcXLKwMxJR43S7fPfQLusDBzvT");
    private static final PublicKey DEFAULT_TOKEN_SEARCH =
            PublicKey.valueOf("So11111111111111111111111111111111111111112");
    private static final String MARKET_ID_ATTRIBUTE_NAME = "marketId";
    private static final String DEFAULT_TOKEN_ATTRIBUTE_NAME = "defaultTokenId";

    private final TokenManager tokenManager;
    private final MarketManager marketManager;
    private final MarketRankManager marketRankManager;

    public IndexController(TokenManager tokenManager,
                           MarketManager marketManager,
                           MarketRankManager marketRankManager) {
        this.tokenManager = tokenManager;
        this.marketManager = marketManager;
        this.marketRankManager = marketRankManager;
    }

    @RequestMapping("/")
    public String index(Model model) {
        Map<String, Token> tokenMap = new HashMap<>();
        tokenManager.getRegistry().forEach((tokenMint, token) -> {
            // only show tokens which have a serum market
            if (marketManager.numMarketsByToken(tokenMint) > 0) {
                tokenMap.put(tokenMint, token);
            }
        });

        model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, DEFAULT_TOKEN_SEARCH.toBase58());
        model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, DEFAULT_MARKET.toBase58());
        model.addAttribute(
                "tokens",
                tokenMap
        );
        model.addAttribute(marketRankManager);
        return "index";
    }

    // for now, return index with the market determined.
    // todo - return the market detail page, doesn't have the search windows (more detail in their place)
    @RequestMapping("/{market}")
    public String market(Model model, @PathVariable String market) {
        // pass default to model in case lookup fails
        model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, DEFAULT_TOKEN_SEARCH.toBase58());
        model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, DEFAULT_MARKET.toBase58());

        // determine market id based on what user gave us
        if (market.contains("-")) {
            // they passed a readable name like "SOL-USDC", since pubkeys can't have dashes
            // get top ranked market for their pair.
            String[] parts = market.split("-");

            if (parts.length == 2) {
                // "sanitization" even though there is no DB to hack
                String baseSymbol = parts[0].replaceAll("[^a-zA-Z]", "");
                String quoteSymbol = parts[1].replaceAll("[^a-zA-Z]", "");

                // todo - bestMostActiveTokenBySymbol in marketrankamanger
                Optional<Token> baseToken = marketRankManager.getMostSerumActiveTokenBySymbol(baseSymbol);
                Optional<Token> quoteToken = marketRankManager.getMostSerumActiveTokenBySymbol(quoteSymbol);

                if (baseToken.isPresent() && quoteToken.isPresent()) {
                    Optional<Market> optionalMarket = marketRankManager.getMostActiveMarket(
                            baseToken.get().getAddress(),
                            quoteToken.get().getAddress()
                    );

                    // put it's id into the model
                    optionalMarket.ifPresent(value -> {
                        model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, value.getBaseMint().toBase58());
                        model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, value.getOwnAddress().toBase58());
                    });
                }
            }
        } else if (market.length() > 30) {
            // exact market id was passed like "9wFFyRfZBsuAha4YcuxcXLKwMxJR43S7fPfQLusDBzvT"
            // pubkeys are like 43 or 44 chars... 30 is enough of a check lol
            String sanitized = market.replaceAll("[^a-zA-Z\\d]", "");
            Optional<Market> sanitizedMarket = marketManager.getMarketById(sanitized);
            if (sanitizedMarket.isPresent()) {
                model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, sanitizedMarket.get().getBaseMint().toBase58());
                model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, sanitizedMarket.get().getOwnAddress().toBase58());
            }

            // check if it's a token mint.
            Token token = tokenManager.getTokenByMint(sanitized);
            if (token != null) {
                Optional<Market> optionalMarket = marketRankManager.getMostActiveMarket(token.getAddress());
                if (optionalMarket.isPresent()) {
                    model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, token.getAddress());
                    model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, optionalMarket.get().getOwnAddress().toBase58());
                }
            }
        } else {
            // try to match it to a symbol e.g. "SRM", choose best market with that base
            String sanitized = market.replaceAll("[^a-zA-Z\\d]", "").toUpperCase();
            List<Token> possibleBaseTokens = tokenManager.getTokensBySymbol(sanitized);
            List<Market> activeMarkets = new ArrayList<>();
            for (Token baseToken : possibleBaseTokens) {
                // compile list of markets, return one with most fees accrued.
                Optional<Market> optionalMarket = marketRankManager.getMostActiveMarket(baseToken.getAddress());
                optionalMarket.ifPresent(activeMarkets::add);

            }
            activeMarkets.sort(Comparator.comparingLong(Market::getQuoteFeesAccrued).reversed());

            if (activeMarkets.size() > 0) {
                model.addAttribute(DEFAULT_TOKEN_ATTRIBUTE_NAME, activeMarkets.get(0).getBaseMint().toBase58());
                model.addAttribute(MARKET_ID_ATTRIBUTE_NAME, activeMarkets.get(0).getOwnAddress().toBase58());
            }
        }

        Map<String, Token> tokenMap = new HashMap<>();
        tokenManager.getRegistry().forEach((tokenMint, token) -> {
            // only show tokens which have a serum market
            if (marketManager.numMarketsByToken(tokenMint) > 0) {
                tokenMap.put(tokenMint, token);
            }
        });

        model.addAttribute(
                "tokens",
                tokenMap
        );
        model.addAttribute(marketRankManager);
        return "index";
    }
}
