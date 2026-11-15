package com.localjobagent.service;

import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
// Removed the import com.localjobagent.config.ScraperConfig;
import com.localjobagent.service.ScraperConfig;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * AGENT 1 - SCRAPER (Session-Authenticated)
 *
 * Uses Playwright with injected session cookies to bypass the LinkedIn login wall
 * and capture job listings as a logged-in user.
 */
@Service
public class JobScraperService {

    private static final Logger log = LoggerFactory.getLogger(JobScraperService.class);
    private final Random random = new Random();
    private final Gson gson = new Gson();
    private final ScraperConfig config;

    public JobScraperService(ScraperConfig config) {
        this.config = config;
    }

    public record ScrapedJob(String title, String company, String description, String link) {}

    public List<ScrapedJob> scrapeJobs() {
        List<ScrapedJob> allJobs = new ArrayList<>();
        List<String> targetUrls = config.getEndpointList();

        if (targetUrls == null || targetUrls.isEmpty()) {
            log.error("Scraper: No target URLs configured in application.properties!");
            return allJobs;
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080));

            // --- SESSION AUTHENTICATION ---
            loadCookies(context);

            Page page = context.newPage();
            page.setDefaultTimeout(60_000);

            for (String url : targetUrls) {
                log.info("Scraper: navigating to {}", url);
                try {
                    page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60_000));

                    try {
                        page.waitForSelector(config.getContainerSelector(), new Page.WaitForSelectorOptions().setTimeout(15_000));
                    } catch (PlaywrightException e) {
                        String currentTitle = page.title();
                        log.warn("Scraper: timed out waiting for selector {} at {}. Page Title: '{}'.",
                                config.getContainerSelector(), url, currentTitle);
                        if (currentTitle.contains("Login") || currentTitle.contains("Sign in")) {
                            log.error("Scraper: CRITICAL - Session cookies may have expired. Redirected to login.");
                        }
                        continue;
                    }

                    String content = page.content();
                    Document doc = Jsoup.parse(content);
                    Elements cards = doc.select(config.getContainerSelector());
                    log.info("Scraper: found {} job cards for URL {}", cards.size(), url);

                    for (Element card : cards) {
                        String title = selectText(card, config.getTitleSelector());
                        String description = selectText(card, config.getDescriptionSelector());
                        String link = selectLink(card, config.getLinkSelector());

                        if (title.isBlank() || link.isBlank()) continue;

                        String company = selectText(card, ".base-search-card__subtitle");
                        if (company.isBlank()) company = "Unknown Company";

                        allJobs.add(new ScrapedJob(title, company, description, link));
                    }

                    Thread.sleep(3000 + random.nextInt(4000));

                } catch (Exception e) {
                    log.error("Scraper: failed to fetch {} - {}", url, e.getMessage());
                }
            }

            browser.close();
        } catch (Exception e) {
            log.error("Scraper: Playwright initialization failed - {}", e.getMessage());
        }
        return allJobs;
    }

    private void loadCookies(BrowserContext context) {
        try {
            String cookiePath = "src/main/resources/linkedin-cookies.json";
            String json = Files.readString(Paths.get(cookiePath));
            List<CookieModel> cookies = gson.fromJson(json, new TypeToken<List<CookieModel>>(){}.getType());

            List<com.microsoft.playwright.options.Cookie> playwrightCookies = cookies.stream().map(c -> {
                com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(c.name, c.value);
                cookie.setDomain(c.domain);
                cookie.setPath(c.path);
                cookie.setSecure(c.secure);
                cookie.setHttpOnly(c.httpOnly);
                return cookie;
            }).collect(Collectors.toList());

            context.addCookies(playwrightCookies);
            log.info("Scraper: successfully loaded {} session cookies from {}", playwrightCookies.size(), cookiePath);
        } catch (Exception e) {
            log.error("Scraper: failed to load session cookies - {}. Falling back to guest view.", e.getMessage());
        }
    }

    private static class CookieModel {
        String domain;
        String name;
        String value;
        String path;
        boolean secure;
        boolean httpOnly;
        String sameSite;
    }

    private String selectText(Element card, String selector) {
        Element el = card.selectFirst(selector);
        return el != null ? el.text().trim() : "";
    }

    private String selectLink(Element card, String selector) {
        Element el = card.selectFirst(selector);
        if (el == null) return "";
        return el.hasAttr("abs:href") ? el.attr("abs:href") : el.attr("href");
    }
}
